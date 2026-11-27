package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for SQL Server's shipped init lock ({@link SQLServerSqlStatements#acquireInitLock()},
 * {@code @LockOwner='Transaction'}), mirroring {@link PostgresInitLockRaceTest} for the dialect that
 * doesn't share Postgres's {@code pg_locks} introspection: this verifies the lock's actual behavior
 * directly over two connections through the same statements the production code ships, rather than
 * reproducing the historical duplicate-row symptom (SQL Server's own failure mode for two concurrent
 * {@code CREATE TABLE}s is a blocking wait on the schema lock, not a distinguishing error, so the
 * duplicate-key reproduction {@code PostgresInitLockRaceTest} uses doesn't carry over).
 * <p>
 * A transaction-owned application lock ({@code sp_getapplock ... @LockOwner='Transaction'}) must be
 * held for the life of the transaction and released automatically at its end, whether by commit or by
 * rollback -- not by the no-op {@link SQLServerSqlStatements#releaseInitLock()} call, which (same as
 * the acquire statement) is one of the exact strings {@code AbstractSqlRegistryStorage.initialize()}
 * sends.
 */
class SqlServerInitLockRaceTest {

    private static MSSQLServerContainer container;

    @BeforeAll
    static void startSqlServer() {
        container = new MSSQLServerContainer(
                DockerImageName.parse("mcr.microsoft.com/mssql/server").withTag("2022-latest"))
                .acceptLicense();
        container.start();
    }

    @AfterAll
    static void stopSqlServer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void secondConnectionBlocksUntilTheFirstCommits() throws Exception {
        assertSecondConnectionBlocksUntilTheFirstEnds(true);
    }

    @Test
    void secondConnectionBlocksUntilTheFirstRollsBack() throws Exception {
        assertSecondConnectionBlocksUntilTheFirstEnds(false);
    }

    private void assertSecondConnectionBlocksUntilTheFirstEnds(boolean commit) throws Exception {
        SQLServerSqlStatements statements = new SQLServerSqlStatements();
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch firstMayEnd = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                try (Connection conn = container.createConnection("")) {
                    conn.setAutoCommit(false);
                    try (Statement stmt = conn.createStatement()) {
                        assertLockAcquired(stmt, statements);
                        stmt.execute(statements.releaseInitLock());
                    }
                    firstAcquired.countDown();
                    if (!firstMayEnd.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("first connection was never told it may end");
                    }
                    if (commit) {
                        conn.commit();
                    } else {
                        conn.rollback();
                    }
                    return null;
                }
            });

            assertTrue(firstAcquired.await(10, TimeUnit.SECONDS),
                    "first connection did not acquire the lock in time");

            Future<?> second = pool.submit(() -> {
                try (Connection conn = container.createConnection("")) {
                    conn.setAutoCommit(false);
                    try (Statement stmt = conn.createStatement()) {
                        // Blocks here until the first connection's transaction ends (commit or
                        // rollback), since the lock is transaction-owned, not released by the
                        // explicit (no-op) releaseInitLock() call above.
                        assertLockAcquired(stmt, statements);
                        stmt.execute(statements.releaseInitLock());
                    }
                    conn.commit();
                    return null;
                }
            });

            assertThrows(TimeoutException.class, () -> second.get(2, TimeUnit.SECONDS),
                    "expected the second connection to still be blocked on the transaction-owned lock");

            firstMayEnd.countDown();
            first.get(10, TimeUnit.SECONDS);
            // Once the first connection's transaction ends, the lock releases and the second
            // connection's acquire goes through.
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Executes {@link SQLServerSqlStatements#acquireInitLock()} on the given statement and asserts
     * that {@code sp_getapplock} actually reports success (a return code &gt;= 0), not merely that the
     * call completed without a JDBC exception. {@code sp_getapplock} communicates failure -- including
     * the {@code -999} "not called from within a transaction" error that this test caught
     * {@code acquireInitLock()} returning the first time it was written, which the driver surfaces as
     * a perfectly ordinary result set rather than a thrown exception -- only through this return code.
     */
    private static void assertLockAcquired(Statement stmt, SQLServerSqlStatements statements)
            throws Exception {
        boolean hasResultSet = stmt.execute(statements.acquireInitLock());
        Integer result = null;
        while (hasResultSet) {
            try (ResultSet rs = stmt.getResultSet()) {
                if (rs.next()) {
                    result = rs.getInt(1);
                }
            }
            hasResultSet = stmt.getMoreResults();
        }
        assertTrue(result != null && result >= 0,
                "sp_getapplock did not report success, returned: " + result);
    }
}
