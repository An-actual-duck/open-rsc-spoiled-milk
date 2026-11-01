package com.openrsc.server.combat;

import com.openrsc.server.database.JDBCDatabase;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/** Prove asynchronous JDBC attempts cannot hide behind a successful loot marker. */
public final class OfflineDropLoggingFixture {
    private OfflineDropLoggingFixture() {}

    public static void main(String[] args) throws Exception {
        CurrentCombatHarness harness = new CurrentCombatHarness();
        try {
            harness.recordDropLogs();
            Future<?> forbidden = harness.server().submitSqlLogging(() -> {
                ((JDBCDatabase) harness.server().getDatabase()).getConnection();
            });
            boolean rejected = false;
            try {
                forbidden.get();
            } catch (ExecutionException expected) {
                rejected = expected.getCause() instanceof AssertionError;
            }
            require(rejected, "JDBC must be rejected before connecting");
            rejected = false;
            try {
                harness.awaitRecordedDropLogs();
            } catch (AssertionError expected) {
                rejected = true;
            }
            require(rejected, "An ignored logging future must still fail the fixture barrier");
            rejected = false;
            try {
                harness.close();
            } catch (AssertionError expected) {
                rejected = true;
            }
            require(rejected, "Teardown must retain an asynchronous database failure");
            rejected = false;
            try {
                harness.server().submitSqlLogging(() -> {});
            } catch (RejectedExecutionException expected) {
                rejected = true;
            }
            require(rejected, "The failed fixture must still terminate its logging executor");
        } finally {
            harness.close();
        }
        System.out.println("Offline drop logging: PASS JDBC guard, ignored-future detection and completed teardown");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
