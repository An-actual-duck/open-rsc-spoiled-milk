package com.openrsc.server.content.market;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Bank;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Game-event economic settlement. Only detached containers are changed before COMMIT. */
public final class MarketTransactions {
    private static final Logger LOGGER = LogManager.getLogger();
    private final World world;
    private final MarketJdbcStore store;

    public MarketTransactions(World world, MarketJdbcStore store) { this.world = world; this.store = store; }

    public void execute(MarketOperation op) {
        Player p = op.actor;
        if (p == null || p.getWorld() != world || !op.hasCurrentActor()) return;
        if (p.getConfig().WANT_THREADING__BREAK_PID_PRIORITY) {
            p.message("The auction is unavailable with experimental concurrent player events enabled.");
            return;
        }
        synchronized (p.getEconomicStateLock()) {
            synchronized (p.getBank().getItems()) {
                synchronized (p.getCarriedItems().getInventory().getItems()) {
                    if (!op.hasCurrentActor()) return;
                    Inventory inventory = p.getCarriedItems().getInventory().copyForTransaction();
                    Bank bank = p.getBank().copyForTransaction();
                    boolean committed = false;
                    try {
                        committed = store.transact(op, c -> {
                            settle(c, op, inventory, bank);
                            store.saveContainers(c, op.actorId, inventory.getItems(), bank.getItems());
                        });
                        if (committed) {
                            store.checkpoint("before-publish");
                            p.getCarriedItems().getInventory().publishTransaction(inventory);
                            p.getBank().publishTransaction(bank);
                        }
                    } catch (MarketJdbcStore.UnresolvedCommit ex) {
                        LOGGER.error("Unresolved market commit {}; detaching player {} for ordinary reload", op.id, op.actorId, ex);
                        p.requireEconomicReload();
                        return;
                    } catch (SQLException | RuntimeException ex) {
                        if (committed) {
                            LOGGER.error("Committed market state could not be published {}; detaching player {}", op.id, op.actorId, ex);
                            p.requireEconomicReload();
                        } else {
                            p.message("The auction could not be completed. Your items and coins have not changed. Please refresh and try again.");
                            LOGGER.warn("Market operation {} did not commit", op.id, ex);
                        }
                        return;
                    }
                    // All external publication follows the durable commit. No queued
                    // player save is needed; inventory/bank are already persisted.
                    if (committed) {
                        ActionSender.sendInventory(p);
                        if (p.accessingBank()) ActionSender.showBank(p);
                        p.message("Auction transaction completed. Refresh the auction to see the updated listings.");
                    } else p.message("That auction request was already completed.");
                }
            }
        }
    }

    private void settle(Connection c, MarketOperation op, Inventory inventory, Bank bank) throws SQLException {
        Player p = op.actor;
        if (!op.hasCurrentActor()) throw new SQLException("Auction session changed");
        if (p.isIronMan(IronmanMode.Ironman.id()) || p.isIronMan(IronmanMode.Ultimate.id())
                || p.isIronMan(IronmanMode.Hardcore.id()) || p.isIronMan(IronmanMode.Transfer.id()))
            throw new SQLException("Ironman accounts cannot use the auction");
        switch (op.kind) {
            case BUY: {
                MarketItem listing = requireListing(c, op.auctionId);
                if (listing.getSeller() == op.actorId) throw new SQLException("Cannot buy your own auction");
                ItemDefinition def = definition(listing.getCatalogID());
                if (def.isUntradable()) throw new SQLException("This legacy item is no longer tradable");
                int amount = Math.min(op.amount, listing.getAmountLeft());
                if (amount <= 0) throw new SQLException("Invalid auction quantity");
                int unit = listing.getPrice() / listing.getAmountLeft();
                long total = (long) amount * unit;
                if (unit <= 0 || total <= 0 || total > Integer.MAX_VALUE) throw new SQLException("Invalid auction price");
                debit(inventory, ItemId.COINS.id(), (int) total, false);
                credit(p, inventory, bank, listing.getCatalogID(), amount, false);
                store.checkpoint("buyer-stage");
                store.creditClaim(c, listing.getSeller(), ItemId.COINS.id(), (int) total, "Auction sale");
                int remaining = listing.getAmountLeft() - amount;
                store.changeListing(c, listing, remaining, Math.multiplyExact(remaining, unit), false);
                break;
            }
            case CREATE: {
                ItemDefinition def = definition(op.catalogId);
                if (op.catalogId == ItemId.COINS.id() || def.isUntradable() || op.amount <= 0 || op.price < op.amount)
                    throw new SQLException("Invalid auction listing");
                debit(inventory, op.catalogId, op.amount, true);
                store.checkpoint("seller-stage");
                store.createListing(c, op, p.getUsername());
                break;
            }
            case CANCEL: {
                MarketItem listing = requireListing(c, op.auctionId);
                if (listing.getSeller() != op.actorId) throw new SQLException("Only the seller may cancel this auction");
                credit(p, inventory, bank, listing.getCatalogID(), listing.getAmountLeft(), false);
                store.changeListing(c, listing, 0, 0, true);
                break;
            }
            case MODERATE: {
                if (!p.isMod()) throw new SQLException("Moderator permission required");
                MarketItem listing = requireListing(c, op.auctionId);
                store.creditClaim(c, listing.getSeller(), listing.getCatalogID(), listing.getAmountLeft(), "Removed by moderator");
                store.changeListing(c, listing, 0, 0, true);
                break;
            }
            case COLLECT: {
                int count = 0;
                for (CollectibleItem claim : store.claims(c, op.actorId)) {
                    if (claim.item_amount <= 0) throw new SQLException("Invalid auction claim amount");
                    if (!bank.canHold(new Item(claim.item_id, claim.item_amount))) break;
                    credit(p, inventory, bank, claim.item_id, claim.item_amount, true);
                    store.claim(c, op.actorId, claim.claim_id); count++;
                }
                if (count == 0) throw new SQLException("No auction claims fit in the bank");
                break;
            }
            default: throw new SQLException("Unsupported player auction operation");
        }
    }

    private MarketItem requireListing(Connection c, int id) throws SQLException {
        MarketItem item = store.listing(c, id);
        if (item == null || item.getAmountLeft() <= 0 || item.getAmountLeft() > item.getAmount())
            throw new SQLException("Auction unavailable; refresh the list");
        return item;
    }

    private ItemDefinition definition(int catalog) throws SQLException {
        ItemDefinition def = world.getServer().getEntityHandler().getItemDef(catalog);
        if (def == null) throw new SQLException("Unknown auction item");
        return def;
    }

    private void credit(Player player, Inventory inventory, Bank bank, int catalog, int amount, boolean bankOnly) throws SQLException {
        ItemDefinition def = definition(catalog);
        if (amount <= 0 || (player.getConfig().RESTRICT_ITEM_ID >= 0 && player.getConfig().RESTRICT_ITEM_ID < catalog))
            throw new SQLException("Item is not available in this world");
        boolean note = !def.isStackable() && amount > 1;
        Item delivery = new Item(catalog, amount, note);
        if (!bankOnly && catalog <= player.getClientLimitations().maxItemId && (!note || def.isNoteable())
                && inventory.canHold(delivery)) {
            if (!inventory.add(delivery, false)) throw new SQLException("Auction inventory admission failed");
            return;
        }
        if (!bank.canHold(new Item(catalog, amount)) || !bank.add(new Item(catalog, amount), false))
            throw new SQLException("No space in inventory or bank");
    }

    private void debit(Inventory inventory, int catalog, int amount, boolean allowNotes) throws SQLException {
        long available = 0;
        for (Item item : inventory.getItems()) if (item.getCatalogId() == catalog && (allowNotes || !item.getNoted()) && !item.isWielded())
            available += item.getAmount();
        if (amount <= 0 || available < amount) throw new SQLException("Not enough unequipped items or coins");
        int remaining = amount;
        for (Item item : new ArrayList<Item>(inventory.getItems())) {
            if (remaining == 0) break;
            if (item.getCatalogId() != catalog || (!allowNotes && item.getNoted()) || item.isWielded()) continue;
            int take = Math.min(remaining, item.getAmount());
            Item exact = item.copyForTransaction(); exact.setAmount(take);
            if (inventory.remove(exact, false) == -1) throw new SQLException("Auction item admission changed");
            remaining -= take;
        }
        if (remaining != 0) throw new SQLException("Incomplete auction debit");
    }

    public java.util.ArrayList<MarketItem> activeListings() throws SQLException { return store.activeListings(); }

    public boolean expire(MarketItem cached) {
        MarketOperation op = MarketOperation.expiry(cached.getAuctionID());
        try {
            return store.transact(op, c -> {
                MarketItem item = store.listing(c, op.auctionId);
                if (item == null || item.getHoursLeft() > 0) throw new SQLException("Auction is not expired");
                store.creditClaim(c, item.getSeller(), item.getCatalogID(), item.getAmountLeft(), "Expired");
                store.changeListing(c, item, 0, 0, false);
            });
        } catch (SQLException | RuntimeException ex) { LOGGER.warn("Auction expiry {} will be retried", op.auctionId, ex); return false; }
    }
}
