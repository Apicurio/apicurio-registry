package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two threads concurrently creating the FIRST version of an artifact must get versionOrder 1 and 2.
 *
 * <p>No CI workflow activates {@code -Pbyteman} yet, so this runs locally only until #9845 is
 * settled. Run it with
 * {@code ./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=ConcurrentVersionCreationTest}.
 * The Byteman section of DEVELOPING.md explains the separate source root and {@code @BMUnitConfig}.
 */
@QuarkusTest
@TestProfile(ConcurrentVersionCreationTest.LockTimeoutProfile.class)
@WithByteman
@BMUnitConfig
@Isolated
public class ConcurrentVersionCreationTest {

    private static final String OPENAPI_V1 = """
            {"openapi": "3.0.2", "info": {"title": "Race V1", "version": "1.0.0"}}""";

    private static final String OPENAPI_V2 = """
            {"openapi": "3.0.2", "info": {"title": "Race V2", "version": "1.0.1"}}""";

    private static final String VERSION_A = "1.0.0";

    private static final String VERSION_B = "2.0.0";

    /** The rule fires only on threads with this prefix, which scopes it to this test. */
    private static final String RACE_THREAD_PREFIX = "versionOrder-race-";

    /**
     * The rule records each writer's rendezvous ordinal under this prefix. A completed rendezvous
     * hands out 0 and 1; -1 means a writer crossed the barrier alone.
     */
    private static final String PROP_ARRIVAL_PREFIX = "byteman.rendezvousArrival.";

    private static final int[] ARRIVAL_SLOTS = {0, 1, -1};

    private static final String RENDEZVOUS_ID = "versionOrder-race";

    private static final String GROUP_ID = ConcurrentVersionCreationTest.class.getSimpleName();

    private static final int RENDEZVOUS_TIMEOUT_MS = 15_000;

    private static final long RESULT_TIMEOUT_MS = 2L * RENDEZVOUS_TIMEOUT_MS;

    /** A thread parked in a rendezvous ignores interrupts, so draining must outlast the barrier. */
    private static final long DRAIN_TIMEOUT_MS = RENDEZVOUS_TIMEOUT_MS + 5_000L;

    /** The barrier releases both writers together but one can still win outright, so a single race
     *  can pass against unfixed code. Each iteration races on a fresh artifact. */
    private static final int RACE_ITERATIONS = 10;

    /**
     * Under the fix the losing writer waits on the winner's artifact row lock. H2 2.4 gives up after
     * 2000 ms by default, which a loaded CI runner can exceed on correct code.
     */
    public static class LockTimeoutProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.datasource.url", "jdbc:h2:mem:db_${quarkus.uuid};LOCK_TIMEOUT="
                    + RENDEZVOUS_TIMEOUT_MS);
        }
    }

    @Inject
    @Current
    RegistryStorage storage;

    @AfterEach
    void clearArrivalProperties() {
        clearArrivals();
    }

    /**
     * <p>The artifact starts with zero versions, so both writers take the first-version branch of
     * insertVersion and write versionOrder = 1. A lock on version rows matches nothing here, which is
     * why the fix locks the parent artifact row. Do not seed a first version.
     *
     * <p>The barrier sits right after ensureContentAndGetId. Below the row lock the second writer
     * would block on the lock and never reach it. At method entry the content hashing and insert
     * would sit between the barrier and the versionOrder read, letting one writer finish first.
     *
     * <p>The writers submit different content, so they do not serialize on the content unique
     * constraint, and distinct version names, so UQ_versions_3 is the only constraint they can break.
     * With a null name both would be named "1" and also collide on UQ_versions_1.
     */
    @Test
    @BMRule(name = "hold both first-version writers at the same line before either inserts",
        targetClass = "io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage",
        targetMethod = "createArtifactVersion",
        targetLocation = "AFTER INVOKE ensureContentAndGetId",
        condition = "java.lang.Thread.currentThread().getName().startsWith(\""
                + RACE_THREAD_PREFIX + "\")",
        // A second createRendezvous with the same id is a no-op, and a completed rendezvous deletes
        // itself, so the fixed id can be reused across iterations.
        action = "createRendezvous(\"" + RENDEZVOUS_ID + "\", 2, false);"
                + "java.lang.System.setProperty(\"" + PROP_ARRIVAL_PREFIX + "\""
                + " + rendezvous(\"" + RENDEZVOUS_ID + "\", " + RENDEZVOUS_TIMEOUT_MS + "),"
                + " \"true\")")
    public void testConcurrentFirstVersionCreationGetsDifferentVersionOrder() throws Exception {
        for (int iteration = 0; iteration < RACE_ITERATIONS; iteration++) {
            runOneRace(iteration);
        }
    }

    private void runOneRace(int iteration) throws Exception {
        String artifactId = TestUtils.generateArtifactId("versionOrderRace" + iteration);
        clearArrivals();

        // Null content creates the artifact with no versions.
        storage.createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, null, null,
                null, null, null, false, false, null);

        ExecutorService executor = raceExecutor();
        ArtifactVersionMetaDataDto resultA;
        ArtifactVersionMetaDataDto resultB;
        boolean drained;
        try {
            Future<ArtifactVersionMetaDataDto> futureA = submitInRequestScope(executor,
                    () -> storage.createArtifactVersion(
                            GROUP_ID, artifactId, VERSION_A, ArtifactType.OPENAPI,
                            contentOf(OPENAPI_V1),
                            null, List.of(), false, false, null));

            Future<ArtifactVersionMetaDataDto> futureB = submitInRequestScope(executor,
                    () -> storage.createArtifactVersion(
                            GROUP_ID, artifactId, VERSION_B, ArtifactType.OPENAPI,
                            contentOf(OPENAPI_V2),
                            null, List.of(), false, false, null));

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESULT_TIMEOUT_MS);
            try {
                resultA = futureA.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                resultB = futureB.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                AssertionError timeout = new AssertionError("Iteration " + iteration
                        + " timed out after " + RESULT_TIMEOUT_MS + "ms waiting for the racing"
                        + " creates. A done=" + futureA.isDone() + ", B done=" + futureB.isDone()
                        + ", " + arrivalSnapshot(), e);
                attachFailure(timeout, futureA);
                attachFailure(timeout, futureB);
                throw timeout;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof VersionAlreadyExistsException) {
                    // createArtifactVersion reports every unique violation under the requested
                    // version name, but the names are distinct, so this is UQ_versions_3.
                    throw new AssertionError("Iteration " + iteration + ": concurrent"
                            + " first-version creates collided on versionOrder. The second insert"
                            + " violated UQ_versions_3 (groupId, artifactId, versionOrder), which"
                            + " means both threads took the same order.", e);
                }
                e.addSuppressed(new IllegalStateException(
                        "iteration " + iteration + ", " + arrivalSnapshot()));
                attachFailure(e, futureA);
                attachFailure(e, futureB);
                throw e;
            }
        } finally {
            executor.shutdownNow();
            drained = awaitQuietly(executor);
        }

        Assertions.assertTrue(drained, "Worker threads did not finish after shutdownNow");

        // Without these, a rule that stopped matching would pass as two sequential creates.
        Assertions.assertNull(arrival(-1),
                "a writer passed the barrier without meeting anyone");
        Assertions.assertEquals("true", arrival(0),
                "first writer should have arrived at the Byteman rendezvous");
        Assertions.assertEquals("true", arrival(1),
                "second writer should have arrived at the Byteman rendezvous");

        Assertions.assertEquals(VERSION_A, resultA.getVersion(),
                "Thread A must keep its explicit version name");
        Assertions.assertEquals(VERSION_B, resultB.getVersion(),
                "Thread B must keep its explicit version name");

        // Set.copyOf, because Set.of throws on the duplicate this assertion exists to report.
        Assertions.assertEquals(Set.of(1, 2),
                Set.copyOf(List.of(resultA.getVersionOrder(), resultB.getVersionOrder())),
                "Iteration " + iteration + ": concurrent first-version creates must produce"
                        + " versionOrder 1 and 2, but got " + resultA.getVersionOrder() + " and "
                        + resultB.getVersionOrder());
        Assertions.assertEquals(2L, storage.countArtifactVersions(GROUP_ID, artifactId),
                "Both versions must be persisted");
    }

    private static ExecutorService raceExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(2,
                r -> new Thread(r, RACE_THREAD_PREFIX + counter.incrementAndGet()));
    }

    /** Keeps an interrupt from replacing the test's real failure inside the finally block. */
    private static boolean awaitQuietly(ExecutorService executor) {
        try {
            return executor.awaitTermination(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Attaches the future's failure unless it is the one target already carries as its cause. */
    private static void attachFailure(Throwable target, Future<?> future) {
        if (future.isDone()) {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (e.getCause() != target.getCause()) {
                    target.addSuppressed(e.getCause());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String arrival(int ordinal) {
        return System.getProperty(PROP_ARRIVAL_PREFIX + ordinal);
    }

    private static void clearArrivals() {
        for (int slot : ARRIVAL_SLOTS) {
            System.clearProperty(PROP_ARRIVAL_PREFIX + slot);
        }
    }

    private static String arrivalSnapshot() {
        StringBuilder snapshot = new StringBuilder("arrivals");
        for (int slot : ARRIVAL_SLOTS) {
            snapshot.append(' ').append(slot).append('=').append(arrival(slot));
        }
        return snapshot.toString();
    }

    private static ContentWrapperDto contentOf(String content) {
        return ContentWrapperDto.builder()
                .contentType(ContentTypes.APPLICATION_JSON)
                .content(ContentHandle.create(content))
                .build();
    }

    private static <T> Future<T> submitInRequestScope(ExecutorService executor, Callable<T> task) {
        return executor.submit(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                return task.call();
            } finally {
                requestContext.terminate();
            }
        });
    }
}
