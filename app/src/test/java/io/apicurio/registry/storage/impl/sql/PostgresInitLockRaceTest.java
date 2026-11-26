package io.apicurio.registry.storage.impl.sql;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a race between two replicas initializing the same, previously empty
 * PostgreSQL database at once: each replica's init runs inside one transaction (the handle
 * commits only after the whole acquire-lock/check/DDL/release callback returns). A
 * <em>session</em>-scoped advisory lock (the old {@code pg_advisory_lock}/{@code
 * pg_advisory_unlock}) is released by the explicit unlock call inside that callback, which runs
 * <strong>before</strong> the commit. A second replica can then acquire the lock while the first
 * replica's DDL is still uncommitted, see the (not-yet-visible) schema as "not initialized", and
 * race to create it too -- producing a duplicate-key error on two concurrent {@code CREATE
 * TABLE}s rather than a clean "already initialized, skipping".
 * <p>
 * A <em>transaction</em>-scoped advisory lock ({@code pg_advisory_xact_lock}, with its matching
 * no-op release) is instead held until the transaction commits or rolls back, so a second replica
 * cannot even acquire the lock -- let alone wrongly see an empty database -- until the first
 * replica's DDL is durable.
 * <p>
 * Rather than racing two threads against the clock (flaky, and only an indirect check), this
 * drives replica one up to -- but not through -- its commit under explicit control, then checks
 * whether replica two's lock acquisition can complete before that commit happens. That is the
 * exact condition the fix changes, and it is observable without any arbitrary sleep.
 */
class PostgresInitLockRaceTest {

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws IOException {
        postgres = EmbeddedPostgres.start();
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void oldSessionScopedLockLetsASecondReplicaInBeforeTheFirstCommits() throws Exception {
        // The lock/release pair this code used to ship with, reproduced directly (not read from
        // PostgreSQLSqlStatements, which no longer has it) so the regression stays pinned even if
        // the fixed statements are refactored later.
        String oldAcquire = "SELECT pg_advisory_lock(918273645), 1";
        String oldRelease = "SELECT CASE WHEN pg_advisory_unlock(918273645) THEN 1 ELSE 0 END";

        assertReplicaTwoCanAcquireBeforeReplicaOneCommits("race_old_lock", oldAcquire, oldRelease, true);
    }

    @Test
    void shippedTransactionScopedLockBlocksASecondReplicaUntilTheFirstCommits() throws Exception {
        PostgreSQLSqlStatements statements = new PostgreSQLSqlStatements();

        assertReplicaTwoCanAcquireBeforeReplicaOneCommits("race_fixed_lock", statements.acquireInitLock(),
                statements.releaseInitLock(), false);
    }

    /**
     * Drives replica one through acquire-lock, create-table, release (but holds it just short of
     * its commit), then has replica two attempt to acquire the same lock and check whether the
     * table exists -- the exact sequence {@code AbstractSqlRegistryStorage.initialize()} runs.
     *
     * @param expectReplicaTwoToGetIn whether replica two's acquire+check is expected to complete
     *            before replica one's commit (the bug) or to still be blocked on the acquire at
     *            that point (the fix)
     */
    private void assertReplicaTwoCanAcquireBeforeReplicaOneCommits(String tableName, String acquireSql,
            String releaseSql, boolean expectReplicaTwoToGetIn) throws Exception {
        CountDownLatch replicaOneReleased = new CountDownLatch(1);
        CountDownLatch replicaOneMayCommit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> replicaOneCreated = pool.submit(() -> {
                try (Connection conn = postgres.getPostgresDatabase().getConnection()) {
                    conn.setAutoCommit(false);
                    boolean created;
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(acquireSql);
                        boolean exists = tableExists(conn, tableName);
                        created = !exists;
                        if (!exists) {
                            stmt.execute("CREATE TABLE " + tableName + " (id INT PRIMARY KEY)");
                        }
                        stmt.execute(releaseSql);
                    }
                    replicaOneReleased.countDown();
                    replicaOneMayCommit.await(10, TimeUnit.SECONDS);
                    conn.commit();
                    return created;
                }
            });

            assertTrue(replicaOneReleased.await(10, TimeUnit.SECONDS),
                    "replica one did not reach its release call in time");

            Future<Boolean> replicaTwoSawUninitialized = pool.submit(() -> {
                try (Connection conn = postgres.getPostgresDatabase().getConnection()) {
                    conn.setAutoCommit(false);
                    boolean sawUninitialized;
                    try (Statement stmt = conn.createStatement()) {
                        // Blocks here for the fixed, transaction-scoped lock (replica one still
                        // holds it); returns promptly for the old, session-scoped one (replica one
                        // already released it, above).
                        stmt.execute(acquireSql);
                        sawUninitialized = !tableExists(conn, tableName);
                        stmt.execute(releaseSql);
                    }
                    conn.commit();
                    return sawUninitialized;
                }
            });

            if (expectReplicaTwoToGetIn) {
                assertTrue(replicaTwoSawUninitialized.get(5, TimeUnit.SECONDS),
                        "expected the old session-scoped lock to let replica two acquire and wrongly "
                                + "see an uninitialized database before replica one's commit");
                replicaOneMayCommit.countDown();
                assertTrue(replicaOneCreated.get(10, TimeUnit.SECONDS), "replica one must have created the table");
            } else {
                assertThrows(TimeoutException.class,
                        () -> replicaTwoSawUninitialized.get(2, TimeUnit.SECONDS),
                        "expected the transaction-scoped lock to block replica two's acquire until "
                                + "replica one's commit");

                replicaOneMayCommit.countDown();
                assertTrue(replicaOneCreated.get(10, TimeUnit.SECONDS), "replica one must have created the table");
                // Once replica one has committed, replica two's acquire, which was still blocked
                // above, goes through, and it must now correctly see the table as already there.
                assertFalse(replicaTwoSawUninitialized.get(10, TimeUnit.SECONDS),
                        "replica two must see the table as initialized once replica one has committed");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws java.sql.SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                        .executeQuery("SELECT 1 FROM information_schema.tables WHERE table_name = '"
                                + tableName + "'")) {
            return rs.next();
        }
    }
}
