package com.openrsc.server.content.market;

import com.openrsc.server.Server;
import com.openrsc.server.database.JDBCDatabase;
import com.openrsc.server.model.container.Item;
import java.sql.*;
import java.util.*;

/** Market-owned physical connections: receipts, escrow and player containers commit together. */
public class MarketJdbcStore {
    public interface Connections { Connection open() throws SQLException; }
    public interface Mutation { void run(Connection connection) throws SQLException; }
    public static final class UnresolvedCommit extends SQLException {
        UnresolvedCommit(SQLException cause) { super("Market commit could not be resolved; session reload required", cause); }
    }
    private final Connections connections;
    private final String prefix;
    private volatile boolean schemaReady;

    public MarketJdbcStore(Server server) {
        this(() -> {
            if (!(server.getDatabase() instanceof JDBCDatabase)) throw new SQLException("Market requires a JDBC provider");
            return ((JDBCDatabase) server.getDatabase()).getConnection().openIsolatedConnection();
        }, server.getConfig().DB_TABLE_PREFIX);
    }

    /** Explicit connection ownership also permits disposable engine integration tests. */
    public MarketJdbcStore(Connections connections, String prefix) {
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid table prefix");
        this.connections = connections; this.prefix = prefix;
    }

    String table(String name) { return "`" + prefix + name + "`"; }

    protected void checkpoint(String stage) throws SQLException { }

    private Connection openOwned() throws SQLException {
        Connection c = connections.open();
        try (Statement s = c.createStatement()) {
            if (isSqlite(c)) s.execute("PRAGMA busy_timeout=2000");
            else s.execute("SET SESSION innodb_lock_wait_timeout=2");
        } catch (SQLException ex) { try { c.close(); } catch (SQLException close) { ex.addSuppressed(close); } throw ex; }
        return c;
    }

    private synchronized void ensureSchema() throws SQLException {
        if (schemaReady) return;
        // MySQL DDL implicitly commits. Run it on a separate autocommit connection
        // before an operation has read or changed any economic state.
        try (Connection c = openOwned(); Statement s = c.createStatement()) {
            boolean sqlite = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("market_operations")
                + " (operation_id VARCHAR(36) PRIMARY KEY, actor_id INTEGER NOT NULL,"
                + " operation_kind VARCHAR(16) NOT NULL, request_key VARCHAR(160) NOT NULL, created BIGINT NOT NULL)"
                + (sqlite ? "" : " ENGINE=InnoDB"));
        }
        try (Connection c = openOwned()) {
            if (!isSqlite(c)) {
                for (String name : Arrays.asList("players", "auctions", "expired_auctions", "invitems", "bank", "equipped", "itemstatuses", "market_operations")) {
                    try (PreparedStatement s = c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
                        s.setString(1, prefix + name);
                        try (ResultSet r = s.executeQuery()) {
                            if (!r.next() || !"InnoDB".equalsIgnoreCase(r.getString(1))) throw new SQLException("Auction safety requires InnoDB table: " + prefix + name);
                        }
                    }
                }
            }
        }
        schemaReady = true;
    }

    private static boolean isSqlite(Connection c) throws SQLException {
        return c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
    }

    private boolean receipt(Connection c, MarketOperation op, boolean locking) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT actor_id,operation_kind,request_key FROM "
                + table("market_operations") + " WHERE operation_id=?" + (locking && !isSqlite(c) ? " FOR UPDATE" : ""))) {
            s.setString(1, op.id.toString());
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return false;
                if (rs.getInt(1) != op.actorId || !rs.getString(2).equals(op.kind.name()) || !rs.getString(3).equals(op.requestKey()))
                    throw new SQLException("Market receipt identity mismatch");
                return true;
            }
        }
    }

    /** Returns false only for a previously committed replay; body never ran then. */
    public boolean transact(MarketOperation op, Mutation body) throws SQLException {
        ensureSchema();
        boolean bodyRan = false;
        SQLException failure;
        try (Connection c = openOwned()) {
            c.setAutoCommit(false);
            try {
                if (receipt(c, op, false)) { c.rollback(); return false; }
                if (op.actorId > 0) {
                    try (PreparedStatement s = c.prepareStatement("SELECT id FROM " + table("players") + " WHERE id=?" + (isSqlite(c) ? "" : " FOR UPDATE"))) {
                        s.setInt(1, op.actorId); try (ResultSet r = s.executeQuery()) { if (!r.next()) throw new SQLException("Auction account no longer exists"); }
                    }
                }
                bodyRan = true;
                body.run(c);
                checkpoint("before-receipt");
                try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table("market_operations")
                        + " (operation_id,actor_id,operation_kind,request_key,created) VALUES(?,?,?,?,?)")) {
                    s.setString(1, op.id.toString()); s.setInt(2, op.actorId); s.setString(3, op.kind.name());
                    s.setString(4, op.requestKey()); s.setLong(5, System.currentTimeMillis()); s.executeUpdate();
                }
                checkpoint("before-commit"); c.commit(); checkpoint("after-commit");
                return true;
            } catch (SQLException | RuntimeException ex) {
                failure = ex instanceof SQLException ? (SQLException) ex : new SQLException("Market operation failed", ex);
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            }
        } catch (SQLException ex) {
            failure = ex;
        }
        // COMMIT may have succeeded before its acknowledgement failed. Never
        // infer rollback from an exception: consult the durable receipt fresh.
        try (Connection check = openOwned()) {
            // A plain SELECT can miss a remote COMMIT still in flight. Locking
            // current reads wait for its receipt insertion/transaction to finish.
            if (isSqlite(check)) {
                try (Statement s = check.createStatement()) { s.execute("BEGIN IMMEDIATE"); }
            } else check.setAutoCommit(false);
            boolean found = receipt(check, op, true);
            if (isSqlite(check)) { try (Statement s = check.createStatement()) { s.execute("ROLLBACK"); } }
            else check.rollback();
            if (found) return bodyRan;
        } catch (SQLException unavailable) {
            failure.addSuppressed(unavailable);
            throw new UnresolvedCommit(failure);
        }
        throw failure;
    }

    public MarketItem listing(Connection c, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT auctionID,itemID,amount,amount_left,price,seller,seller_username,buyer_info,time FROM "
                + table("auctions") + " WHERE auctionID=? AND `sold-out`=0")) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) {
                return !r.next() ? null : new MarketItem(r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4),
                    r.getInt(5), r.getInt(6), r.getString(7), r.getString(8), r.getLong(9));
            }
        }
    }

    public void changeListing(Connection c, MarketItem old, int left, int price, boolean cancelled) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE " + table("auctions")
                + " SET amount_left=?,price=?,`sold-out`=?,was_cancel=?,buyer_info=? WHERE auctionID=? AND `sold-out`=0 AND amount_left=? AND price=?")) {
            s.setInt(1, left); s.setInt(2, price); s.setInt(3, left == 0 ? 1 : 0); s.setInt(4, cancelled ? 1 : 0);
            s.setString(5, old.getBuyers()); s.setInt(6, old.getAuctionID()); s.setInt(7, old.getAmountLeft()); s.setInt(8, old.getPrice());
            if (s.executeUpdate() != 1) throw new SQLException("Auction changed; refresh before trying again");
        }
        checkpoint("listing-update");
    }

    public void creditClaim(Connection c, int owner, int catalog, int amount, String explanation) throws SQLException {
        if (owner <= 0 || catalog < 0 || amount <= 0) throw new SQLException("Invalid auction claim");
        try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table("expired_auctions")
                + " (item_id,item_amount,time,playerID,explanation) VALUES(?,?,?,?,?)")) {
            s.setInt(1, catalog); s.setInt(2, amount); s.setLong(3, System.currentTimeMillis() / 1000);
            s.setInt(4, owner); s.setString(5, explanation); s.executeUpdate();
        }
        checkpoint("seller-credit");
    }

    public void createListing(Connection c, MarketOperation op, String name) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table("auctions")
                + " (itemID,amount,amount_left,price,seller,seller_username,buyer_info,time) VALUES(?,?,?,?,?,?,?,?)")) {
            s.setInt(1, op.catalogId); s.setInt(2, op.amount); s.setInt(3, op.amount); s.setInt(4, op.price);
            s.setInt(5, op.actorId); s.setString(6, name); s.setString(7, ""); s.setLong(8, System.currentTimeMillis() / 1000); s.executeUpdate();
        }
        checkpoint("listing-create");
    }

    public List<CollectibleItem> claims(Connection c, int actor) throws SQLException {
        List<CollectibleItem> result = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT claim_id,item_id,item_amount,explanation FROM "
                + table("expired_auctions") + " WHERE playerID=? AND claimed=0 ORDER BY claim_id")) {
            s.setInt(1, actor);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) { CollectibleItem item = new CollectibleItem(); item.claim_id = rs.getInt(1);
                    item.item_id = rs.getInt(2); item.item_amount = rs.getInt(3); item.explanation = rs.getString(4); item.playerID = actor; result.add(item); }
            }
        }
        return result;
    }

    public void claim(Connection c, int actor, int claim) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE " + table("expired_auctions")
                + " SET claimed=1,claim_time=? WHERE claim_id=? AND playerID=? AND claimed=0")) {
            s.setLong(1, System.currentTimeMillis()); s.setInt(2, claim); s.setInt(3, actor);
            if (s.executeUpdate() != 1) throw new SQLException("Auction claim changed");
        }
        checkpoint("claim-update");
    }

    public void saveContainers(Connection c, int actor, List<Item> inventory, List<Item> bank) throws SQLException {
        Set<Long> previousIds = new HashSet<>();
        for (String container : Arrays.asList("invitems", "bank")) {
            try (PreparedStatement s = c.prepareStatement("SELECT itemID FROM " + table(container) + " WHERE playerID=?")) {
                s.setInt(1, actor); try (ResultSet r = s.executeQuery()) { while (r.next()) previousIds.add(r.getLong(1)); }
            }
        }
        Set<Long> ids = new HashSet<>();
        for (List<Item> items : Arrays.asList(inventory, bank)) for (Item item : items) {
            if (item.getItemId() < 0 || item.getAmount() <= 0 || !ids.add(item.getItemId())) throw new SQLException("Invalid container item identity");
            for (String container : Arrays.asList("invitems", "bank", "equipped")) {
                try (PreparedStatement s = c.prepareStatement("SELECT playerID FROM " + table(container) + " WHERE itemID=?" + (container.equals("equipped") ? "" : " AND playerID<>?"))) {
                    s.setLong(1, item.getItemId()); if (!container.equals("equipped")) s.setInt(2, actor);
                    try (ResultSet rs = s.executeQuery()) { if (rs.next()) throw new SQLException("Item belongs to a different player"); }
                }
            }
        }
        for (String container : Arrays.asList("invitems", "bank")) {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table(container) + " WHERE playerID=?")) { s.setInt(1, actor); s.executeUpdate(); }
        }
        writeContainer(c, actor, "invitems", inventory); checkpoint("inventory-save");
        writeContainer(c, actor, "bank", bank); checkpoint("bank-save");
        previousIds.removeAll(ids);
        for (Long removed : previousIds) {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table("itemstatuses") + " WHERE itemID=?"
                    + " AND NOT EXISTS (SELECT 1 FROM " + table("invitems") + " WHERE itemID=?)"
                    + " AND NOT EXISTS (SELECT 1 FROM " + table("bank") + " WHERE itemID=?)"
                    + " AND NOT EXISTS (SELECT 1 FROM " + table("equipped") + " WHERE itemID=?)")) {
                for (int i=1;i<=4;i++) s.setLong(i,removed); s.executeUpdate();
            }
        }
    }

    private void writeContainer(Connection c, int actor, String name, List<Item> items) throws SQLException {
        for (int slot = 0; slot < items.size(); slot++) {
            Item item = items.get(slot);
            try (PreparedStatement s = c.prepareStatement("UPDATE " + table("itemstatuses")
                    + " SET catalogID=?,amount=?,noted=?,wielded=?,durability=? WHERE itemID=?")) {
                bindStatus(s, item);
                if (s.executeUpdate() == 0 && !statusExists(c, item.getItemId())) try (PreparedStatement add = c.prepareStatement("INSERT INTO " + table("itemstatuses")
                        + " (catalogID,amount,noted,wielded,durability,itemID) VALUES(?,?,?,?,?,?)")) { bindStatus(add, item); add.executeUpdate(); }
            }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table(name) + " (playerID,itemID,slot) VALUES(?,?,?)")) {
                s.setInt(1, actor); s.setLong(2, item.getItemId()); s.setInt(3, slot); s.executeUpdate();
            }
        }
    }

    private boolean statusExists(Connection c, long id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT itemID FROM " + table("itemstatuses") + " WHERE itemID=?")) {
            s.setLong(1, id); try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    public ArrayList<MarketItem> activeListings() throws SQLException {
        ArrayList<MarketItem> result = new ArrayList<>();
        try (Connection c = openOwned(); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT auctionID,itemID,amount,amount_left,price,seller,seller_username,buyer_info,time FROM " + table("auctions") + " WHERE `sold-out`=0")) {
            while (r.next()) result.add(new MarketItem(r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getInt(6), r.getString(7), r.getString(8), r.getLong(9)));
        }
        return result;
    }

    private void bindStatus(PreparedStatement s, Item i) throws SQLException {
        s.setInt(1, i.getCatalogId()); s.setInt(2, i.getAmount()); s.setInt(3, i.getNoted() ? 1 : 0);
        s.setInt(4, i.isWielded() ? 1 : 0); s.setInt(5, i.getItemStatus().getDurability()); s.setLong(6, i.getItemId());
    }
}
