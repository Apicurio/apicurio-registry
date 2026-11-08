package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Runs {@link HandleFactory#withRetryingHandle} against a throwaway H2 database with actions that fail on cue.
 * Each attempt inserts a row before it fails or returns, so the row count shows that a failed attempt was
 * rolled back and that exactly the successful one was stored. Outcomes depend only on the classifier and the
 * attempt bound, never on timing.
 */
class HandleFactoryRetryTest {

    private static final int ATTEMPTS = 3;
    private static final String DEADLOCK_STATE = "40P01";
    private static final Predicate<RuntimeException> DEADLOCKS_ONLY = e -> e.getCause() instanceof SQLException
            && DEADLOCK_STATE.equals(((SQLException) e.getCause()).getSQLState());

    private AgroalDataSource dataSource;
    private DefaultHandleFactory handles;

    @BeforeEach
    void createDatabase() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(AgroalPropertiesReader.MAX_SIZE, "2");
        props.put(AgroalPropertiesReader.MIN_SIZE, "1");
        props.put(AgroalPropertiesReader.INITIAL_SIZE, "1");
        props.put(AgroalPropertiesReader.JDBC_URL,
                "jdbc:h2:mem:retry-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        props.put(AgroalPropertiesReader.PRINCIPAL, "sa");
        props.put(AgroalPropertiesReader.CREDENTIAL, "sa");
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, RegistryDatabaseKind.h2.getDriverClassName());
        dataSource = AgroalDataSource.from(new AgroalPropertiesReader().readProperties(props).get());
        handles = new DefaultHandleFactory(dataSource, LoggerFactory.getLogger(HandleFactoryRetryTest.class),
                new ConnectionRetryConfig());
        handles.withHandleNoException((Handle handle) -> {
            handle.createUpdate("CREATE TABLE writes (attempt INT NOT NULL)").execute();
        });
    }

    @AfterEach
    void closeDatabase() {
        dataSource.close();
    }

    @Test
    void aRetryableFailureIsRunAgainAndOnlyTheSuccessfulAttemptIsStored() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = deadlock();
        long retriedBefore = handles.retriedAttempts();

        handles.withRetryingHandle(handle -> writeThenFail(handle, attempts, deadlock), DEADLOCKS_ONLY, ATTEMPTS);

        Assertions.assertEquals(2, attempts.get());
        Assertions.assertEquals(1, storedRows(), "the failed first attempt was rolled back");
        Assertions.assertEquals(1, handles.retriedAttempts() - retriedBefore);
    }

    @Test
    void aNonRetryableFailureIsThrownAfterTheFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException syntaxError = new RuntimeSqlException(new SQLException("syntax error", "42601"));

        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> handles.withRetryingHandle(handle -> writeThenFail(handle, attempts, syntaxError),
                        DEADLOCKS_ONLY, ATTEMPTS));

        Assertions.assertSame(syntaxError, thrown);
        Assertions.assertEquals(1, attempts.get());
        Assertions.assertEquals(0, storedRows());
    }

    @Test
    void aFailureOnEveryAttemptIsThrownOnceTheAttemptsAreUsedUp() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = deadlock();
        long retriedBefore = handles.retriedAttempts();

        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> handles.withRetryingHandle(handle -> {
                    attempts.incrementAndGet();
                    insert(handle);
                    throw deadlock;
                }, DEADLOCKS_ONLY, ATTEMPTS));

        Assertions.assertSame(deadlock, thrown);
        Assertions.assertEquals(ATTEMPTS, attempts.get());
        Assertions.assertEquals(0, storedRows());
        Assertions.assertEquals(ATTEMPTS - 1, handles.retriedAttempts() - retriedBefore);
    }

    /**
     * An interrupt during the pause before a retry ends the call: the original failure is thrown, the retry
     * never runs and is not counted, and the thread keeps its interrupt status. The action sets the flag
     * itself, after the connection was acquired, so the flag is already set while the failed attempt is
     * rolled back and its connection closed, and the pause is the first wait that observes it.
     */
    @Test
    void anInterruptDuringThePauseThrowsTheFailureWithoutCountingARetry() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = deadlock();
        long retriedBefore = handles.retriedAttempts();
        try {
            RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                    () -> handles.withRetryingHandle(handle -> {
                        attempts.incrementAndGet();
                        Thread.currentThread().interrupt();
                        throw deadlock;
                    }, DEADLOCKS_ONLY, ATTEMPTS));

            Assertions.assertSame(deadlock, thrown);
            Assertions.assertEquals(1, attempts.get());
            Assertions.assertEquals(0, handles.retriedAttempts() - retriedBefore);
            Assertions.assertTrue(Thread.currentThread().isInterrupted(), "the interrupt status is restored");
        } finally {
            Thread.interrupted();
        }
    }

    /** Inside an enclosing transaction the action joins it, so a failed attempt is never repeated. */
    @Test
    void anActionInsideAnEnclosingHandleRunsOnce() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException deadlock = deadlock();

        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> handles.withHandleNoException((Handle outer) -> {
                    handles.withRetryingHandle(handle -> writeThenFail(handle, attempts, deadlock),
                            DEADLOCKS_ONLY, ATTEMPTS);
                }));

        Assertions.assertSame(deadlock, thrown);
        Assertions.assertEquals(1, attempts.get());
        Assertions.assertEquals(0, storedRows());
    }

    /**
     * A real H2 duplicate key, raised by a plain insert and by a statement batch, is recognised as the key
     * race of a non-atomic upsert by its error code, and is not mistaken for a transient conflict.
     */
    @Test
    void aRealH2DuplicateKeyIsClassifiedAsTheUpsertKeyRace() {
        handles.withHandleNoException((Handle handle) -> {
            handle.createUpdate("CREATE TABLE keyed (id INT NOT NULL PRIMARY KEY)").execute();
            handle.createUpdate("INSERT INTO keyed (id) VALUES (1)").execute();
        });
        RuntimeException single = Assertions.assertThrows(RuntimeException.class,
                () -> handles.withHandleNoException((Handle handle) -> {
                    handle.createUpdate("INSERT INTO keyed (id) VALUES (1)").execute();
                }));
        RuntimeException batch = Assertions.assertThrows(RuntimeException.class,
                () -> handles.withHandleNoException((Handle handle) -> {
                    handle.createUpdate("INSERT INTO keyed (id) VALUES (?)").bind(0, 2).addBatch().bind(0, 1)
                            .addBatch().executeBatch();
                }));

        for (RuntimeException duplicate : List.of(single, batch)) {
            Assertions.assertTrue(new H2SqlStatements().isUpsertKeyRace(duplicate), duplicate.toString());
            Assertions.assertFalse(new H2SqlStatements().isTransientConflict(duplicate), duplicate.toString());
            Assertions.assertFalse(new PostgreSQLSqlStatements().isUpsertKeyRace(duplicate),
                    "an atomic upsert never retries a duplicate key");
        }
    }

    /** A fresh deadlock failure for each test, so no test sees suppressed exceptions another test added. */
    private static RuntimeException deadlock() {
        return new RuntimeSqlException(new SQLException("deadlock detected", DEADLOCK_STATE));
    }

    /** Inserts a row, then fails with the given error on the first attempt only. */
    private static void writeThenFail(Handle handle, AtomicInteger attempts, RuntimeException failure) {
        int attempt = attempts.incrementAndGet();
        insert(handle);
        if (attempt == 1) {
            throw failure;
        }
    }

    private static void insert(Handle handle) {
        handle.createUpdate("INSERT INTO writes (attempt) VALUES (1)").execute();
    }

    private long storedRows() {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery("SELECT COUNT(*) FROM writes").mapTo(Long.class).one();
        });
    }
}
