package com.openrsc.server.service;

import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.JDBCDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** A fresh login must wait for an earlier, possibly unacknowledged auction COMMIT. */
public final class AccountLoadBarrier {
    private AccountLoadBarrier() { }

    /**
     * Runs before the ordinary provider starts its new snapshot transaction.
     * Applies to all JDBC profiles, including restart with auctions disabled.
     * It never creates auction tables or reads process-local session flags.
     */
    public static int await(GameDatabase database, String prefix, String username) throws SQLException {
        if (!(database instanceof JDBCDatabase)) throw new SQLException("Account loading requires a JDBC settlement barrier");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]*")) throw new SQLException("Invalid account table prefix");
        try (Connection c = ((JDBCDatabase) database).getConnection().openIsolatedConnection()) {
            boolean sqlite = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
            try (Statement s = c.createStatement()) {
                if (sqlite) { s.execute("PRAGMA busy_timeout=2000"); s.execute("BEGIN IMMEDIATE"); }
                else { s.execute("SET SESSION innodb_lock_wait_timeout=2"); c.setAutoCommit(false); }
            }
            try {
                try (PreparedStatement s = c.prepareStatement("SELECT id FROM `" + prefix + "players` WHERE username=?" + (sqlite ? "" : " FOR UPDATE"))) {
                    s.setString(1, username);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new SQLException("Account no longer exists");
                        int id = r.getInt(1);
                        if (id <= 0 || r.next()) throw new SQLException("Ambiguous account identity");
                        return id;
                    }
                }
            } finally {
                // Read-only barrier: release its locks before the ordinary load
                // starts a fresh transaction; a timeout never permits loading.
                if (sqlite) { try (Statement s = c.createStatement()) { s.execute("ROLLBACK"); } }
                else c.rollback();
            }
        }
    }
}
