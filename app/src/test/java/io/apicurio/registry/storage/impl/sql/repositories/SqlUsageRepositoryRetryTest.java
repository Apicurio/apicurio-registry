package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.H2SqlStatements;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.MySQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.PostgreSQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.SQLServerSqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.impl.sql.jdb.Update;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The retry loop itself is covered by HandleFactoryRetryTest. These check what the usage repository asks it
 * to retry, transient conflicts on every dialect and a duplicate key only where the upsert is not atomic,
 * and how it splits a batch into chunks.
 */
class SqlUsageRepositoryRetryTest {

    /** Fresh per call: a test that inspects suppressed exceptions must not see another test's. */
    private static RuntimeException deadlock() {
        return sqlError("deadlock detected", "40P01", 0);
    }

    private static RuntimeException syntax() {
        return sqlError("syntax error", "42601", 0);
    }

    private static RuntimeException h2DuplicateKey() {
        return sqlError("Unique index or primary key violation: PRIMARY_KEY_D", "23505", 23505);
    }

    private static RuntimeException pgDuplicateKey() {
        return sqlError("duplicate key value violates unique constraint", "23505", 0);
    }

    @Test
    void theWriteGoesThroughTheRetryingHandleWithThreeAttempts() {
        HandleFactory handles = mock(HandleFactory.class);

        new SqlUsageRepository(handles, new PostgreSQLSqlStatements()).recordUsageEvents(List.of(
                SchemaUsageEventDto.builder().globalId(1).contentId(0).clientId("app").operation("SERIALIZE")
                        .eventTimestamp(1_000L).build()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<RuntimeException>> classifier = ArgumentCaptor.forClass(Predicate.class);
        verify(handles).withRetryingHandle(any(HandleAction.class), classifier.capture(), eq(3));
        Assertions.assertTrue(classifier.getValue().test(deadlock()));
        Assertions.assertFalse(classifier.getValue().test(syntax()));
    }

    /**
     * 450 keys make three chunks of 200, 200 and 50 rows. The middle chunk fails for good with a data error
     * (SQLState 22001): the first and last chunks are still written, and the middle chunk's failure reaches
     * the caller.
     */
    @Test
    void aDataErrorCostsOnlyItsOwnChunkAndItsFailureSurfaces() {
        RuntimeException dataError = sqlError("value too long", "22001", 0);
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(2, dataError), false);

        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, chunks::write);

        Assertions.assertSame(dataError, thrown.getCause());
        Assertions.assertEquals(
                "usage rows 201 to 400 of 450 were not stored (SQLState 22001, vendor code 0: value too long)",
                thrown.getMessage());
        Assertions.assertEquals(3, chunks.attempted.get(), "the chunk after the failed one is still written");
        Assertions.assertEquals(clientIds(0, 200, 400, 450), chunks.stored, "chunks 1 and 3, in key order");
    }

    /**
     * A data error known only by its vendor code: Connector/J reports MySQL 1366 (incorrect string value)
     * with SQLState HY000. It still costs only the middle chunk.
     */
    @Test
    void aDataErrorKnownOnlyByItsVendorCodeCostsOnlyItsOwnChunk() {
        RuntimeException dataError = sqlError("Incorrect string value", "HY000", 1366);
        Chunks chunks = new Chunks(new MySQLSqlStatements(), false, Map.of(2, dataError), false);

        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, chunks::write);

        Assertions.assertSame(dataError, thrown.getCause());
        Assertions.assertEquals("usage rows 201 to 400 of 450 were not stored (SQLState HY000, vendor code 1366: "
                + "Incorrect string value)", thrown.getMessage());
        Assertions.assertEquals(3, chunks.attempted.get(), "the chunk after the failed one is still written");
        Assertions.assertEquals(clientIds(0, 200, 400, 450), chunks.stored, "chunks 1 and 3, in key order");
    }

    /** A failure that is not about the data, here a lost connection (08006), stops the loop at once. */
    @Test
    void aConnectionErrorStopsTheChunksThatFollow() {
        RuntimeException connectionError = sqlError("connection lost", "08006", 0);
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(1, connectionError), false);

        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, chunks::write);

        Assertions.assertSame(connectionError, thrown.getCause());
        Assertions.assertEquals(RuntimeSqlException.class, thrown.getClass(),
                "KafkaSqlSink logs this type at WARN when a journal record cannot be applied");
        Assertions.assertEquals("usage rows 1 to 450 of 450 were not stored (java.sql.SQLException: connection lost)",
                thrown.getMessage());
        Assertions.assertEquals(1, chunks.attempted.get(), "chunks 2 and 3 are never attempted");
        Assertions.assertEquals(List.of(), chunks.stored);
    }

    /** An interrupted thread stops at the first failure, even a data error. */
    @Test
    void anInterruptedThreadStopsAtTheFirstFailure() {
        RuntimeException dataError = sqlError("value too long", "22001", 0);
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(1, dataError), true);
        try {
            RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class,
                    chunks::write);

            Assertions.assertSame(dataError, thrown.getCause());
            Assertions.assertEquals(
                    "usage rows 1 to 450 of 450 were not stored (SQLState 22001, vendor code 0: value too long)",
                    thrown.getMessage());
            Assertions.assertEquals(1, chunks.attempted.get());
            Assertions.assertTrue(Thread.currentThread().isInterrupted(), "the interrupt status is kept");
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * A write that joined an enclosing transaction stops at the first failure, even a data error: the
     * failure has already made that transaction rollback-only.
     */
    @Test
    void aJoinedTransactionStopsAtTheFirstFailure() {
        RuntimeException dataError = sqlError("value too long", "22001", 0);
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), true, Map.of(1, dataError), false);

        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, chunks::write);

        Assertions.assertSame(dataError, thrown.getCause());
        Assertions.assertEquals("all 450 usage rows were not stored (the enclosing transaction is rolled back)"
                + " (SQLState 22001, vendor code 0: value too long)", thrown.getMessage());
        Assertions.assertEquals(1, chunks.attempted.get());
    }

    /**
     * A data error in the last chunk leaves nothing to continue with, so there is no "continuing" WARN; the
     * exception names that chunk's rows. A control WARN logged through SLF4J during the capture proves the
     * capture receives this logger's records, so the absence is not vacuous.
     */
    @Test
    void aDataErrorInTheLastChunkIsThrownWithoutAContinuingWarning() {
        RuntimeException dataError = sqlError("value too long", "22001", 0);
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(3, dataError), false);

        List<LogRecord> warnings = new ArrayList<>();
        RuntimeSqlException thrown = captureWarnings(warnings, () -> {
            LoggerFactory.getLogger(SqlUsageRepository.class).warn("control");
            return Assertions.assertThrows(RuntimeSqlException.class, chunks::write);
        });

        Assertions.assertEquals(List.of("control"), warnings.stream().map(LogRecord::getMessage).toList());
        Assertions.assertSame(dataError, thrown.getCause());
        Assertions.assertEquals(
                "usage rows 401 to 450 of 450 were not stored (SQLState 22001, vendor code 0: value too long)",
                thrown.getMessage());
        Assertions.assertEquals(clientIds(0, 200, 200, 400), chunks.stored, "chunks 1 and 2");
    }

    /**
     * A data error in chunk 1 continues with one WARN; a lost connection in chunk 2 then stops the flush.
     * The message names both ranges, the stop is the cause and the earlier data error is suppressed.
     */
    @Test
    void aStopAfterADataErrorNamesBothRangesAndIsTheCause() {
        RuntimeException dataError = sqlError("value too long", "22001", 0);
        RuntimeException connectionError = sqlError("connection lost", "08006", 0);
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false,
                Map.of(1, dataError, 2, connectionError), false);

        List<LogRecord> warnings = new ArrayList<>();
        RuntimeSqlException thrown = captureWarnings(warnings,
                () -> Assertions.assertThrows(RuntimeSqlException.class, chunks::write));

        Assertions.assertEquals(1, warnings.size(), "one continuing WARN for chunk 1");
        Assertions.assertSame(connectionError, thrown.getCause());
        Assertions.assertArrayEquals(new Throwable[] { dataError }, thrown.getSuppressed());
        Assertions.assertEquals("usage rows 1 to 200 of 450 were not stored (SQLState 22001, vendor code 0); usage"
                + " rows 201 to 450 of 450 were not stored (java.sql.SQLException: connection lost)",
                thrown.getMessage());
        Assertions.assertEquals(2, chunks.attempted.get());
    }

    /**
     * A failure without an SQLException in its cause chain, such as executeBatch's fail-fast
     * IllegalStateException, is not reported as a database rejection.
     */
    @Test
    void aFailureWithoutAnSqlExceptionIsNotARuntimeSqlException() {
        RuntimeException batchMisuse = new IllegalStateException("executeBatch with unbound parameters");
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(1, batchMisuse), false);

        RegistryStorageException thrown = Assertions.assertThrows(RegistryStorageException.class, chunks::write);

        Assertions.assertSame(batchMisuse, thrown.getCause());
        Assertions.assertEquals("usage rows 1 to 450 of 450 were not stored (executeBatch with unbound parameters)",
                thrown.getMessage());
        Assertions.assertEquals(1, chunks.attempted.get());
    }

    /**
     * The JDBC layer also throws a RuntimeSqlException without an SQLException cause (SqlParam.bindTo does).
     * It is still a database failure, so the type that KafkaSqlSink logs at WARN is kept; without a message
     * the reason is the class name.
     */
    @Test
    void aRuntimeSqlExceptionWithoutAnSqlExceptionStaysARuntimeSqlException() {
        RuntimeException bindFailure = new RuntimeSqlException();
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(1, bindFailure), false);

        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, chunks::write);

        Assertions.assertSame(bindFailure, thrown.getCause());
        Assertions.assertEquals("usage rows 1 to 450 of 450 were not stored ("
                + RuntimeSqlException.class.getName() + ")", thrown.getMessage());
    }

    /**
     * PostgreSQL reports a failed batch row as a BatchUpdateException with the row's SQLState, whose message
     * copies the statement and its bound values, and chains the row's own error as the next exception. The
     * reason comes from that row error.
     */
    @Test
    void theReasonComesFromTheRowErrorNotTheBatchWrapper() {
        BatchUpdateException batch = new BatchUpdateException(
                "Batch entry 0 INSERT INTO schema_usage_daily VALUES (1, 'secret-client') was aborted", "23502", 0,
                new int[0]);
        batch.setNextException(new SQLException("ERROR: null value in column \"clientid\"", "23502"));
        Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false,
                Map.of(2, new RuntimeSqlException(batch)), false);

        RuntimeSqlException thrown = Assertions.assertThrows(RuntimeSqlException.class, chunks::write);

        Assertions.assertEquals("usage rows 201 to 400 of 450 were not stored (SQLState 23502, vendor code 0: "
                + "ERROR: null value in column \"clientid\")", thrown.getMessage());
        Assertions.assertEquals(3, chunks.attempted.get());
    }

    /** A cyclic cause or next-exception chain ends the walk instead of looping forever. */
    @Test
    void aCyclicChainIsClassifiedWithoutLooping() {
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            RuntimeException outer = new RuntimeException("outer");
            outer.initCause(new RuntimeException("inner", outer));
            SqlStatements statements = new PostgreSQLSqlStatements();
            Assertions.assertTrue(statements.findDataError(outer).isEmpty());
            Assertions.assertFalse(statements.isDatabaseFailure(outer));
            Assertions.assertFalse(statements.isTransientConflict(outer));

            SQLException tooLong = new SQLException("value too long", "22001");
            RuntimeSqlException wrapper = new RuntimeSqlException(tooLong);
            tooLong.initCause(wrapper);
            Assertions.assertSame(tooLong, statements.findDataError(wrapper).orElseThrow());

            SQLException first = new SQLException("first", "08000");
            SQLException second = new SQLException("second", "08000");
            first.setNextException(second);
            second.setNextException(first);
            Assertions.assertTrue(statements.findDataError(new RuntimeSqlException(first)).isEmpty());

            Chunks chunks = new Chunks(new PostgreSQLSqlStatements(), false, Map.of(1, outer), false);
            RegistryStorageException thrown = Assertions.assertThrows(RegistryStorageException.class,
                    chunks::write);
            Assertions.assertSame(outer, thrown.getCause());
        });
    }

    /** The retention delete goes through the retrying handle with the same classifier as the writes. */
    @Test
    void theRetentionDeleteIsRetriedOnATransientConflict() throws Exception {
        HandleFactory handles = mock(HandleFactory.class);

        new SqlUsageRepository(handles, new PostgreSQLSqlStatements())
                .deleteOldUsageEvents(3 * SqlUsageRepository.DAY_MS + 5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<RuntimeException>> classifier = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<HandleAction<?>> action = ArgumentCaptor.forClass(HandleAction.class);
        verify(handles).withRetryingHandle(action.capture(), classifier.capture(), eq(3));
        Assertions.assertTrue(classifier.getValue().test(deadlock()));
        Assertions.assertFalse(classifier.getValue().test(syntax()));

        Handle handle = mock(Handle.class);
        Update delete = fluentUpdate();
        when(handle.createUpdate(anyString())).thenReturn(delete);
        action.getValue().withHandle(handle);
        verify(delete).bind(0, 3L);
        verify(delete).execute();
    }

    @Test
    void aDuplicateKeyIsRetriedOnlyWhereTheUpsertIsNotAtomic() {
        Assertions.assertTrue(repository(new H2SqlStatements()).isRetryable(h2DuplicateKey()));
        Assertions.assertFalse(repository(new PostgreSQLSqlStatements()).isRetryable(pgDuplicateKey()));
        Assertions.assertFalse(repository(new H2SqlStatements()).isRetryable(syntax()));
        Assertions.assertTrue(repository(new MySQLSqlStatements()).isRetryable(sqlError("deadlock", "40001",
                1213)));
    }

    /** Each dialect's deadlock codes, and a batch failure that carries the real error as the next one. */
    @Test
    void transientConflictsAreClassifiedBySqlStateAndVendorCode() {
        Assertions.assertTrue(new H2SqlStatements().isTransientConflict(sqlError("deadlock", "40001", 40001)));
        Assertions.assertTrue(new H2SqlStatements().isTransientConflict(sqlError("concurrent update", "90131",
                90131)));
        Assertions.assertFalse(new H2SqlStatements().isTransientConflict(sqlError("lock timeout", "HYT00",
                50200)), "a lock timeout already waited for its full timeout and is not retried");
        Assertions.assertTrue(new MySQLSqlStatements().isTransientConflict(sqlError("deadlock", "40001", 1213)));
        Assertions.assertTrue(new MySQLSqlStatements().isTransientConflict(sqlError("deadlock", null, 1213)));
        Assertions.assertFalse(new MySQLSqlStatements().isTransientConflict(sqlError("Lock wait timeout exceeded",
                "40001", 1205)), "Connector/J reports a lock wait timeout as 40001; it is not retried");
        Assertions.assertTrue(new SQLServerSqlStatements().isTransientConflict(sqlError("victim", null, 1205)));
        Assertions.assertTrue(new PostgreSQLSqlStatements().isTransientConflict(sqlError("serialize", "40001",
                0)));
        Assertions.assertFalse(new PostgreSQLSqlStatements().isTransientConflict(sqlError("concurrent update",
                "90131", 90131)), "90131 is H2's code, not PostgreSQL's");
        Assertions.assertFalse(new PostgreSQLSqlStatements().isTransientConflict(syntax()));

        BatchUpdateException batch = new BatchUpdateException("batch failed", "08000", 0, new int[0]);
        batch.setNextException(new SQLException("deadlock detected", "40P01"));
        Assertions.assertTrue(new PostgreSQLSqlStatements().isTransientConflict(new RuntimeSqlException(batch)));
    }

    /**
     * SQLState class 22 or 23 on every dialect, and the vendor codes whose SQLState the pinned drivers do not
     * report that way: MySQL 1366 comes as HY000, SQL Server 2628 as S0001, and 8152 as 42000 with
     * xopenStates. The classifying exception is returned, also when a batch chains it as the next one.
     */
    @Test
    void dataErrorsAreClassifiedBySqlStateAndVendorCode() {
        SQLException truncation = new SQLException("value too long", "22001", 0);
        Assertions.assertSame(truncation, new PostgreSQLSqlStatements()
                .findDataError(new RuntimeSqlException(truncation)).orElseThrow());
        Assertions.assertTrue(new H2SqlStatements().findDataError(h2DuplicateKey()).isPresent());
        Assertions.assertTrue(new MySQLSqlStatements().findDataError(sqlError("Incorrect string value", "HY000",
                1366)).isPresent());
        Assertions.assertTrue(new MySQLSqlStatements().findDataError(sqlError("Data too long", "HY000", 1406))
                .isPresent());
        Assertions.assertTrue(new SQLServerSqlStatements().findDataError(sqlError("would be truncated", "S0001",
                2628)).isPresent());
        Assertions.assertTrue(new SQLServerSqlStatements().findDataError(sqlError("would be truncated", "42000",
                8152)).isPresent());
        Assertions.assertFalse(new MySQLSqlStatements().findDataError(sqlError("Lost connection", "HY000", 2013))
                .isPresent());
        Assertions.assertFalse(new PostgreSQLSqlStatements().findDataError(sqlError("Incorrect string value",
                "HY000", 1366)).isPresent(), "1366 is MySQL's code, not PostgreSQL's");
        Assertions.assertFalse(new SQLServerSqlStatements().findDataError(sqlError("victim", "40001", 1205))
                .isPresent());

        BatchUpdateException batch = new BatchUpdateException("batch failed", "08000", 0, new int[0]);
        SQLException rowError = new SQLException("Incorrect string value", "HY000", 1366);
        batch.setNextException(rowError);
        Assertions.assertSame(rowError, new MySQLSqlStatements().findDataError(new RuntimeSqlException(batch))
                .orElseThrow());
    }

    /** Client ids c000.. of the half-open ranges [from1, to1) and [from2, to2). */
    private static List<String> clientIds(int from1, int to1, int from2, int to2) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 450; i++) {
            if ((i >= from1 && i < to1) || (i >= from2 && i < to2)) {
                ids.add(String.format(Locale.ROOT, "c%03d", i));
            }
        }
        return ids;
    }

    /**
     * Writes 450 keys, three chunks, through a mocked factory whose chunks fail with the failures given by
     * chunk number, optionally interrupting the thread first, using the given dialect's statements
     * (PostgreSQL by default). The other chunks record their rows.
     */
    private static final class Chunks {
        final HandleFactory handles = mock(HandleFactory.class);
        final List<String> stored = new ArrayList<>();
        final AtomicInteger attempted = new AtomicInteger();
        private final List<SchemaUsageEventDto> events = new ArrayList<>();

        private final SqlStatements statements;

        Chunks(SqlStatements statements, boolean joined, Map<Integer, RuntimeException> failures,
                boolean interrupt) {
            this.statements = statements;
            Handle handle = recordingHandle(stored);
            when(handles.isInHandle()).thenReturn(joined);
            doAnswer(invocation -> {
                RuntimeException failure = failures.get(attempted.incrementAndGet());
                if (failure != null) {
                    if (interrupt) {
                        Thread.currentThread().interrupt();
                    }
                    throw failure;
                }
                ((HandleAction<?>) invocation.getArgument(0)).withHandle(handle);
                return null;
            }).when(handles).withRetryingHandle(any(HandleAction.class), any(), eq(3));
            for (int i = 449; i >= 0; i--) {
                events.add(SchemaUsageEventDto.builder().globalId(1).contentId(0)
                        .clientId(String.format(Locale.ROOT, "c%03d", i)).operation("SERIALIZE")
                        .eventTimestamp(1_000L).build());
            }
        }

        void write() {
            new SqlUsageRepository(handles, statements).recordUsageEvents(events);
        }
    }

    /**
     * An Update mock whose fluent calls return the mock itself. Mockito's RETURNS_SELF skips them because
     * Sql&lt;Q&gt; declares bind with the erased return type Object.
     */
    private static Update fluentUpdate() {
        return mock(Update.class, invocation -> invocation.getMethod().getReturnType() != void.class
                && invocation.getMethod().getReturnType().isInstance(invocation.getMock())
                        ? invocation.getMock()
                        : RETURNS_DEFAULTS.answer(invocation));
    }

    /** A handle whose upserts record the client id of every row added to a batch. */
    private static Handle recordingHandle(List<String> stored) {
        Handle handle = mock(Handle.class);
        when(handle.createUpdate(anyString())).thenAnswer(invocation -> {
            Update update = fluentUpdate();
            when(update.bind(eq(2), anyString())).thenAnswer(bind -> {
                stored.add(bind.getArgument(1));
                return update;
            });
            return update;
        });
        return handle;
    }

    /**
     * Runs {@code action} and collects the WARN records of SqlUsageRepository, through a java.util.logging
     * handler as ReferenceGraphWalkerTest does; app/pom.xml routes SLF4J through the JBoss LogManager.
     */
    private static <T> T captureWarnings(List<LogRecord> warnings, Supplier<T> action) {
        Logger logger = Logger.getLogger(SqlUsageRepository.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().equals(Level.WARNING)) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            return action.get();
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static SqlUsageRepository repository(SqlStatements statements) {
        return new SqlUsageRepository(mock(HandleFactory.class), statements);
    }

    private static RuntimeException sqlError(String message, String sqlState, int vendorCode) {
        return new RuntimeSqlException(new SQLException(message, sqlState, vendorCode));
    }
}
