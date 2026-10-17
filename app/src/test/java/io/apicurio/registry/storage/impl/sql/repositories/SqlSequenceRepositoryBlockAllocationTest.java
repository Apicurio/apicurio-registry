package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.PostgreSQLSqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.HandleAction;
import io.apicurio.registry.storage.impl.sql.jdb.HandleCallback;
import io.apicurio.registry.storage.impl.sql.jdb.MappedQuery;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.jdb.RowMapper;
import io.apicurio.registry.storage.impl.sql.jdb.Update;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SqlSequenceRepositoryBlockAllocationTest {

    private static final int BLOCK_SIZE = 50;

    private FakeHandleFactory handles;

    private SqlStatements statements;

    @BeforeEach
    void setUp() {
        statements = new PostgreSQLSqlStatements();
        handles = new FakeHandleFactory(statements);
    }

    private SqlSequenceRepository repository(int blockSize) {
        return new SqlSequenceRepository(handles, statements,
                LoggerFactory.getLogger(SqlSequenceRepositoryBlockAllocationTest.class), blockSize);
    }

    @Test
    void reservesOneBlockPerBlockSizeValues() {
        SqlSequenceRepository repository = repository(BLOCK_SIZE);

        for (int i = 0; i < BLOCK_SIZE; i++) {
            repository.nextGlobalId();
        }
        Assertions.assertEquals(1, handles.isolatedTransactions.get(),
                "One block of " + BLOCK_SIZE + " values should require exactly one reservation");

        repository.nextGlobalId();
        Assertions.assertEquals(2, handles.isolatedTransactions.get(),
                "Exhausting the block should trigger exactly one more reservation");
    }

    @Test
    void handsOutContiguousValuesStartingAtOne() {
        SqlSequenceRepository repository = repository(BLOCK_SIZE);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < BLOCK_SIZE + 5; i++) {
            ids.add(repository.nextGlobalId());
        }

        List<Long> expected = Stream.iterate(1L, id -> id + 1).limit(BLOCK_SIZE + 5L)
                .collect(Collectors.toList());
        Assertions.assertEquals(expected, ids);
    }

    @Test
    void reservationUsesAnIsolatedTransaction() {
        SqlSequenceRepository repository = repository(BLOCK_SIZE);

        repository.nextGlobalId();

        Assertions.assertEquals(1, handles.isolatedTransactions.get(),
                "The block must be reserved on its own connection");
        Assertions.assertEquals(0, handles.sequenceStatementsOnCallerHandle.get(),
                "No sequence statement may run on the caller's handle, or its lock would be held "
                        + "until the caller's transaction commits");
    }

    @Test
    void blockSizeOfOneReservesEachValueOnTheCallerHandle() {
        SqlSequenceRepository repository = repository(1);

        repository.nextGlobalId();
        repository.nextGlobalId();

        Assertions.assertEquals(0, handles.isolatedTransactions.get(),
                "Block allocation must be disabled entirely when the block size is 1");
        Assertions.assertEquals(2, handles.sequenceStatementsOnCallerHandle.get());
        Assertions.assertEquals(2L, handles.sequences.get(SqlSequenceRepository.GLOBAL_ID_SEQUENCE));
    }

    @Test
    void sequencesAreAllocatedIndependently() {
        SqlSequenceRepository repository = repository(BLOCK_SIZE);

        Assertions.assertEquals(1L, repository.nextGlobalId());
        Assertions.assertEquals(1L, repository.nextContentId());
        Assertions.assertEquals(1L, repository.nextCommentId());
        Assertions.assertEquals(2L, repository.nextGlobalId());
        Assertions.assertEquals(2L, repository.nextContentId());

        Assertions.assertEquals(3, handles.isolatedTransactions.get(),
                "Each sequence reserves its own block");
        Assertions.assertEquals((long) BLOCK_SIZE,
                handles.sequences.get(SqlSequenceRepository.GLOBAL_ID_SEQUENCE));
        Assertions.assertEquals((long) BLOCK_SIZE,
                handles.sequences.get(SqlSequenceRepository.CONTENT_ID_SEQUENCE));
    }

    @Test
    void concurrentCallersNeverReceiveDuplicateValues() throws Exception {
        SqlSequenceRepository repository = repository(BLOCK_SIZE);

        int threads = 8;
        int idsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    List<Long> ids = new ArrayList<>(idsPerThread);
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(repository.nextGlobalId());
                    }
                    return ids;
                }));
            }
            start.countDown();

            List<Long> all = new ArrayList<>();
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get(60, TimeUnit.SECONDS));
            }

            int total = threads * idsPerThread;
            Assertions.assertEquals(total, all.size());
            Assertions.assertEquals(total, Set.copyOf(all).size(), "Sequence values must be unique");
            Assertions.assertEquals(total / BLOCK_SIZE, handles.isolatedTransactions.get(),
                    "Reservations must stay amortized across the block under concurrency");
        } finally {
            executor.shutdownNow();
            Assertions.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void resetDiscardsTheReservedBlock() {
        SqlSequenceRepository repository = repository(BLOCK_SIZE);

        repository.nextGlobalId();
        Assertions.assertEquals((long) BLOCK_SIZE,
                handles.sequences.get(SqlSequenceRepository.GLOBAL_ID_SEQUENCE));

        handles.maxGlobalId = 5000L;
        repository.resetGlobalId();

        Assertions.assertEquals(5000L,
                handles.sequences.get(SqlSequenceRepository.GLOBAL_ID_SEQUENCE));
        Assertions.assertEquals(5001L, repository.nextGlobalId(),
                "Values reserved before the reset must be discarded rather than replayed");
    }

    private static class FakeHandleFactory implements HandleFactory {

        final Map<String, Long> sequences = new ConcurrentHashMap<>();

        final AtomicInteger isolatedTransactions = new AtomicInteger();

        final AtomicInteger sequenceStatementsOnCallerHandle = new AtomicInteger();

        final SqlStatements statements;

        volatile Long maxGlobalId;

        FakeHandleFactory(SqlStatements statements) {
            this.statements = statements;
        }

        @Override
        public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
            return callback.withHandle(new FakeHandle(this, false));
        }

        @Override
        public <R, X extends Exception> R withHandleNoException(HandleCallback<R, X> callback) {
            try {
                return withHandle(callback);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <X extends Exception> void withHandleNoException(HandleAction<X> action) {
            withHandleNoException(handle -> {
                action.withHandle(handle);
                return null;
            });
        }

        @Override
        public <R, X extends Exception> R withIsolatedHandleNoException(HandleCallback<R, X> callback) {
            isolatedTransactions.incrementAndGet();
            try {
                return callback.withHandle(new FakeHandle(this, true));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        synchronized long addAndGet(String sequenceName, long increment) {
            long value = sequences.getOrDefault(sequenceName, 0L) + increment;
            sequences.put(sequenceName, value);
            return value;
        }
    }

    private static class FakeHandle implements Handle {

        private final FakeHandleFactory factory;

        private final boolean isolated;

        FakeHandle(FakeHandleFactory factory, boolean isolated) {
            this.factory = factory;
            this.isolated = isolated;
        }

        @Override
        public Query createQuery(String sql) {
            return new FakeQuery(factory, isolated, sql);
        }

        @Override
        public Update createUpdate(String sql) {
            return new FakeUpdate(factory, isolated, sql);
        }

        @Override
        public void setRollback(boolean rollback) {
        }

        @Override
        public void close() {
        }
    }

    private abstract static class FakeSql {

        protected final FakeHandleFactory factory;

        protected final boolean isolated;

        protected final String sql;

        protected final Map<Integer, Object> binds = new HashMap<>();

        FakeSql(FakeHandleFactory factory, boolean isolated, String sql) {
            this.factory = factory;
            this.isolated = isolated;
            this.sql = sql;
        }

        protected Optional<Long> evaluate() {
            SqlStatements statements = factory.statements;
            if (sql.equals(statements.getNextSequenceValueBlock())) {
                Assertions.assertTrue(isolated,
                        "Block reservation must run on an isolated handle, not the caller's transaction");
                Assertions.assertEquals(binds.get(1), binds.get(2),
                        "Initial value and increment must both be the block size");
                return Optional.of(factory.addAndGet((String) binds.get(0), (Long) binds.get(2)));
            }
            if (sql.equals(statements.getNextSequenceValue())) {
                if (!isolated) {
                    factory.sequenceStatementsOnCallerHandle.incrementAndGet();
                }
                return Optional.of(factory.addAndGet((String) binds.get(0), 1L));
            }
            if (sql.equals(statements.selectCurrentSequenceValue())) {
                return Optional.ofNullable(factory.sequences.get((String) binds.get(0)));
            }
            if (sql.equals(statements.resetSequenceValue())) {
                factory.sequences.put((String) binds.get(0), (Long) binds.get(1));
                return Optional.of((Long) binds.get(1));
            }
            if (sql.equals(statements.selectMaxGlobalId())) {
                return Optional.ofNullable(factory.maxGlobalId);
            }
            throw new AssertionError("Unexpected statement issued by the sequence repository: " + sql);
        }

        protected void put(int position, Object value) {
            binds.put(position, value);
        }
    }

    private static class FakeQuery extends FakeSql implements Query {

        FakeQuery(FakeHandleFactory factory, boolean isolated, String sql) {
            super(factory, isolated, sql);
        }

        @Override
        public Query setFetchSize(int size) {
            return this;
        }

        @Override
        public <T> MappedQuery<T> map(RowMapper<T> mapper) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> MappedQuery<T> mapTo(Class<T> someClass) {
            Assertions.assertEquals(Long.class, someClass);
            return (MappedQuery<T>) new FakeMappedQuery(evaluate());
        }

        @Override
        public Query bind(int position, String value) {
            put(position, value);
            return this;
        }

        @Override
        public Query bind(int position, Long value) {
            put(position, value);
            return this;
        }

        @Override
        public Query bind(int position, Integer value) {
            put(position, value == null ? null : Long.valueOf(value.longValue()));
            return this;
        }

        @Override
        public Query bind(int position, Enum<?> value) {
            put(position, value);
            return this;
        }

        @Override
        public Query bind(int position, Date value) {
            put(position, value);
            return this;
        }

        @Override
        public Query bind(int position, byte[] value) {
            put(position, value);
            return this;
        }

        @Override
        public Query bind(int position, Boolean value) {
            put(position, value);
            return this;
        }
    }

    private static class FakeUpdate extends FakeSql implements Update {

        FakeUpdate(FakeHandleFactory factory, boolean isolated, String sql) {
            super(factory, isolated, sql);
        }

        @Override
        public int execute() {
            evaluate();
            return 1;
        }

        @Override
        public void executeNoUpdate() {
            evaluate();
        }

        @Override
        public Update bind(int position, String value) {
            put(position, value);
            return this;
        }

        @Override
        public Update bind(int position, Long value) {
            put(position, value);
            return this;
        }

        @Override
        public Update bind(int position, Integer value) {
            put(position, value == null ? null : Long.valueOf(value.longValue()));
            return this;
        }

        @Override
        public Update bind(int position, Enum<?> value) {
            put(position, value);
            return this;
        }

        @Override
        public Update bind(int position, Date value) {
            put(position, value);
            return this;
        }

        @Override
        public Update bind(int position, byte[] value) {
            put(position, value);
            return this;
        }

        @Override
        public Update bind(int position, Boolean value) {
            put(position, value);
            return this;
        }
    }

    private record FakeMappedQuery(Optional<Long> value) implements MappedQuery<Long> {

        @Override
        public Long one() {
            return value.orElseThrow(() -> new AssertionError("No value returned by statement"));
        }

        @Override
        public Long first() {
            return one();
        }

        @Override
        public Optional<Long> findOne() {
            return value;
        }

        @Override
        public Optional<Long> findFirst() {
            return value;
        }

        @Override
        public Optional<Long> findLast() {
            return value;
        }

        @Override
        public List<Long> list() {
            return value.map(List::of).orElse(Collections.emptyList());
        }

        @Override
        public Stream<Long> stream() {
            return value.stream();
        }
    }
}
