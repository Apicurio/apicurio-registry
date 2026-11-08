package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.noprofile.storage.AbstractUsageStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.impl.sql.repositories.SqlUsageRepository;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The concurrency tests write to a version created for each test and read only that version's rows. The
 * ordering tests seed every key before the writers start, so the concurrent phase only updates existing rows:
 * it takes no gap, insert-intention or key-range locks, and H2's MERGE has no insert to race on. The storage
 * writes every batch in one key order, so those updates never deadlock on any dialect, and any retry means
 * the order broke, even if the totals still come out right after the retry.
 */
@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@Typed(DefaultUsageStorageTest.class)
public class DefaultUsageStorageTest extends AbstractUsageStorageTest {

    private static final long DAY_MS = SqlUsageRepository.DAY_MS;
    private static final int THREADS = 8;
    private static final int ROUNDS = 50;
    private static final int WRITES_PER_ROUND = 3;
    private static final List<String> KEY_NAMES = List.of("a", "B", "c", "D");

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    /**
     * Several replicas, or the flush and another writer, can add to the same day row at once. In every round
     * all threads wait on one barrier and then write a key that does not exist yet, so the first writes race
     * on the insert and the rest on the increment; many rounds make the insert race near certain to happen.
     * The key is deliberately not seeded. Even threads write one event per call, odd threads a batch of two.
     * Each row must hold every event of its round and the widest timestamps. Only PostgreSQL must need no
     * retry: its ON CONFLICT upsert waits for the first inserter and then updates. The race legitimately
     * needs retries on H2, whose MERGE is not atomic, and on MySQL, where concurrent inserts of one new key
     * can deadlock on their shared and exclusive record locks. SQL Server is excluded as well: MERGE WITH
     * (HOLDLOCK) takes key-range locks that can conflict on an insert race, and it cannot be run locally to
     * verify otherwise.
     */
    @Test
    public void concurrentWritersOfOneKeyLoseNoIncrement() throws Exception {
        String prefix = TestUtils.generateGroupId("concurrent") + "-";
        long globalId = createVersion(prefix);
        long day = SqlUsageRepository.usageDay(System.currentTimeMillis()) - 1;
        long base = day * DAY_MS + 1_000;

        long retries = runRounds((thread, round, i) -> {
            String client = prefix + round + "-k";
            long timestamp = base + thread * 1_000L + i;
            if (thread % 2 == 0) {
                storage.recordUsageEvent(event(globalId, client, timestamp));
            } else {
                storage.recordUsageEvents(
                        List.of(event(globalId, client, timestamp), event(globalId, client, timestamp)));
            }
        });

        long expected = (THREADS / 2) * WRITES_PER_ROUND + (THREADS / 2) * WRITES_PER_ROUND * 2;
        for (int round = 0; round < ROUNDS; round++) {
            Assertions.assertEquals(List.of(prefix + round + "-k|" + day + "|" + expected + "|" + base + "|"
                    + lastTimestamp(base)), rows(globalId, prefix + round + "-"), "round " + round);
        }
        if (insertRaceNeedsNoRetry()) {
            assertNoRetries(retries);
        }
    }

    /**
     * Four keys per round are seeded first, so the concurrent phase only updates existing rows. Every batch
     * then carries one event for each of the four keys, even threads listing them in one order and odd
     * threads in the reverse order. The storage writes a batch in its own key order, so the batches lock
     * shared rows in the same order and neither deadlock nor lose an increment.
     */
    @Test
    public void concurrentBatchesOverSharedKeysInOppositeOrdersLoseNoIncrement() throws Exception {
        String prefix = TestUtils.generateGroupId("multikey") + "-";
        long globalId = createVersion(prefix);
        long day = SqlUsageRepository.usageDay(System.currentTimeMillis()) - 1;
        long base = day * DAY_MS + 1_000;

        List<SchemaUsageEventDto> seed = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            for (String name : KEY_NAMES) {
                seed.add(event(globalId, prefix + round + "-" + name, base));
            }
        }
        storage.recordUsageEvents(seed);

        long retries = runRounds((thread, round, i) -> {
            long timestamp = base + thread * 1_000L + i;
            List<SchemaUsageEventDto> batch = new ArrayList<>();
            for (String name : KEY_NAMES) {
                batch.add(event(globalId, prefix + round + "-" + name, timestamp));
            }
            storage.recordUsageEvents(thread % 2 == 0 ? batch : batch.reversed());
        });

        for (int round = 0; round < ROUNDS; round++) {
            List<String> expected = new ArrayList<>();
            for (String name : KEY_NAMES) {
                expected.add(prefix + round + "-" + name + "|" + day + "|" + (THREADS * WRITES_PER_ROUND + 1)
                        + "|" + base + "|" + lastTimestamp(base));
            }
            Assertions.assertEquals(expected.stream().sorted().toList(), rows(globalId, prefix + round + "-"),
                    "round " + round);
        }
        assertNoRetries(retries);
    }

    /**
     * MySQL and SQL Server compare client ids case-insensitively, so "App" and "app" are one row there and
     * two rows on H2 and PostgreSQL. Even threads write {App, Zed}, odd threads {Zed, app}: unless the
     * storage keeps "App" and "app" adjacent in its write order, the two batches lock that row and "Zed" in
     * opposite orders.
     */
    @Test
    public void clientIdsThatDifferOnlyInCaseAreLockedInOneOrder() throws Exception {
        String prefix = TestUtils.generateGroupId("caseorder") + "-";
        long globalId = createVersion(prefix);
        long day = SqlUsageRepository.usageDay(System.currentTimeMillis()) - 1;
        long base = day * DAY_MS + 1_000;

        List<SchemaUsageEventDto> seed = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            seed.add(event(globalId, prefix + round + "-App", base));
            seed.add(event(globalId, prefix + round + "-app", base));
            seed.add(event(globalId, prefix + round + "-Zed", base));
        }
        storage.recordUsageEvents(seed);

        long retries = runRounds((thread, round, i) -> {
            long timestamp = base + thread * 1_000L + i;
            String other = thread % 2 == 0 ? "App" : "app";
            storage.recordUsageEvents(List.of(event(globalId, prefix + round + "-" + other, timestamp),
                    event(globalId, prefix + round + "-Zed", timestamp)));
        });

        boolean caseInsensitive = clientIdsAreCaseInsensitive();
        // The seed adds one event for App, one for app and one for Zed.
        int all = THREADS * WRITES_PER_ROUND;
        List<String> expected = caseInsensitive ? List.of("app|" + (all + 2), "zed|" + (all + 1))
                : List.of("app|" + (all / 2 + 1), "app|" + (all / 2 + 1), "zed|" + (all + 1));
        for (int round = 0; round < ROUNDS; round++) {
            String roundPrefix = prefix + round + "-";
            List<String> counts = handles.withHandleNoException(handle -> {
                return handle.createQuery("SELECT clientId, fetchCount FROM schema_usage_daily "
                        + "WHERE globalId = ? AND clientId LIKE ?")
                        .bind(0, globalId).bind(1, roundPrefix + "%")
                        .map(rs -> rs.getString("clientId").substring(roundPrefix.length())
                                .toLowerCase(Locale.ROOT) + "|" + rs.getLong("fetchCount"))
                        .list();
            });
            Assertions.assertEquals(expected, counts.stream().sorted().toList(), "round " + round);
        }
        assertNoRetries(retries);
    }

    /** True only where the single-key insert race is known to need no retry; see that test's Javadoc. */
    private boolean insertRaceNeedsNoRetry() {
        return switch (sqlStatements.dbType()) {
            case "postgresql" -> true;
            case "h2", "mysql", "mssql" -> false;
            default -> throw new IllegalStateException("Unknown dialect " + sqlStatements.dbType());
        };
    }

    /** True on MySQL and SQL Server, false on H2 and PostgreSQL, as each database compares client ids. */
    private boolean clientIdsAreCaseInsensitive() {
        return switch (sqlStatements.dbType()) {
            case "mysql", "mssql" -> true;
            case "h2", "postgresql" -> false;
            default -> throw new IllegalStateException("Unknown dialect " + sqlStatements.dbType());
        };
    }

    private static long lastTimestamp(long base) {
        return base + (THREADS - 1) * 1_000L + WRITES_PER_ROUND - 1;
    }

    @FunctionalInterface
    private interface Write {
        void apply(int thread, int round, int index);
    }

    /**
     * Runs ROUNDS rounds on THREADS threads. All threads start a round together on one barrier, then each
     * makes WRITES_PER_ROUND writes. The first failure is rethrown; it interrupts the other writers so that
     * none of them waits on the barrier until its timeout. Returns how many times a write was retried.
     */
    private long runRounds(Write write) throws Exception {
        long retriedBefore = retriedAttempts();
        CyclicBarrier roundStart = new CyclicBarrier(THREADS);
        AtomicReference<Exception> firstFailure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                writers.add(pool.submit(() -> {
                    try {
                        for (int round = 0; round < ROUNDS; round++) {
                            roundStart.await(60, TimeUnit.SECONDS);
                            for (int i = 0; i < WRITES_PER_ROUND; i++) {
                                write.apply(thread, round, i);
                            }
                        }
                    } catch (Exception e) {
                        firstFailure.compareAndSet(null, e);
                        pool.shutdownNow();
                    }
                }));
            }
            for (Future<?> writer : writers) {
                writer.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            Assertions.assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "writers did not stop");
        }
        if (firstFailure.get() != null) {
            throw firstFailure.get();
        }
        return retriedAttempts() - retriedBefore;
    }

    private void assertNoRetries(long retries) {
        Assertions.assertEquals(0, retries, "writes in one key order must never need a retry on "
                + sqlStatements.dbType());
    }

    private long retriedAttempts() {
        return ((AbstractHandleFactory) ClientProxy.unwrap(handles)).retriedAttempts();
    }

    private long createVersion(String group) {
        return UsageRows.createVersion(storage, group);
    }

    @Override
    protected List<String> rows(long globalId, String clientPrefix) {
        return UsageRows.rows(handles, globalId, clientPrefix);
    }

    private static SchemaUsageEventDto event(long globalId, String client, long timestamp) {
        return SchemaUsageEventDto.builder().globalId(globalId).contentId(0).clientId(client)
                .operation("SERIALIZE").eventTimestamp(timestamp).build();
    }
}
