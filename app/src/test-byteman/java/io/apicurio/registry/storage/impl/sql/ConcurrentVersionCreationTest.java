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
import jakarta.inject.Inject;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
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
 * Regression test for two threads concurrently creating the FIRST version of an artifact.
 *
 * <p>Run with:
 * {@code ./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=ConcurrentVersionCreationTest}.
 * See the Byteman section of DEVELOPING.md for why this lives in its own source root. That root
 * compiles only under the profile, so the class needs no enabled-if guard, which could only turn
 * a broken agent setup into a silent skip.
 */
@QuarkusTest
@WithByteman
// Required even with the defaults, or the next Byteman class in the JVM fails its setup.
// See the Byteman section of DEVELOPING.md.
@BMUnitConfig
// The rule fires only on threads this test names, so it cannot reach another test's calls.
// @Isolated is belt and braces for any future parallel run.
@Isolated
public class ConcurrentVersionCreationTest {

    private static final String OPENAPI_V1 = """
            {"openapi": "3.0.2", "info": {"title": "Race V1", "version": "1.0.0"}}""";

    private static final String OPENAPI_V2 = """
            {"openapi": "3.0.2", "info": {"title": "Race V2", "version": "1.0.1"}}""";

    /** Semver-valid, so the test does not silently depend on apicurio.semver.validation.enabled
     *  staying false. See the test javadoc for why they cannot be null. */
    private static final String VERSION_A = "1.0.0";

    private static final String VERSION_B = "2.0.0";

    /** Arms the rule. A Byteman condition cannot see test state, but it can see the thread it
     *  runs on, so naming the worker threads scopes the rule to this test without a JVM-global
     *  flag that would have to be armed and disarmed around the fixture. */
    private static final String RACE_THREAD_PREFIX = "versionOrder-race-";

    /**
     * The rule records each thread's arrival ordinal under this prefix, so the test can prove the
     * two writers really met. A completed two-party rendezvous hands out ordinals 0 and 1.
     * Helper.rendezvous returns -1 instead when the rendezvous is absent, deleted or already
     * complete, which writes the "-1" key and fails the assertions below, because it means a
     * writer crossed the barrier alone. A rendezvous that times out writes nothing at all:
     * Helper.rendezvous throws ExecuteException while the setProperty argument is still being
     * evaluated, and Byteman rethrows it out of createArtifactVersion rather than swallowing it,
     * so it surfaces as an ExecutionException from Future.get.
     */
    private static final String PROP_ARRIVAL_PREFIX = "byteman.rendezvousArrival.";

    private static final int[] ARRIVAL_SLOTS = {0, 1, -1};

    private static final String RENDEZVOUS_ID = "versionOrder-race";

    private static final String GROUP_ID = ConcurrentVersionCreationTest.class.getSimpleName();

    private static final int RENDEZVOUS_TIMEOUT_MS = 15_000;

    /** Twice the barrier budget, so a stuck rendezvous normally expires first. */
    private static final long RESULT_TIMEOUT_MS = 2L * RENDEZVOUS_TIMEOUT_MS;

    /** Past the barrier deadline, because a thread parked in a rendezvous ignores interrupts. */
    private static final long DRAIN_TIMEOUT_MS = RENDEZVOUS_TIMEOUT_MS + 5_000L;

    /** The barrier releases both writers together but does not constrain what happens next, so a
     *  single race can still be won outright by one thread and pass against unfixed code. Each
     *  iteration is an independent race on a fresh artifact. */
    private static final int RACE_ITERATIONS = 10;

    @Inject
    @Current
    RegistryStorage storage;

    @AfterEach
    void clearArrivalProperties() {
        clearArrivals();
    }

    /**
     * Verifies that two threads concurrently creating the FIRST version of an artifact get
     * distinct versionOrder values.
     *
     * <p>The artifact is created with null content, so it starts with zero versions. That detail
     * is the whole point of this test and must not be "simplified" by seeding a first version.
     * With zero versions both writers count zero and take the first-version branch of
     * insertVersion, which writes a literal versionOrder = 1. A lock taken on version rows would
     * match nothing here and serialize nobody, which is why the fix locks the parent row.
     *
     * <p>Coordination is a two-party Byteman rendezvous placed immediately after the
     * ensureContentAndGetId call inside createArtifactVersion. The window is narrow at both ends.
     * It has to stay upstream of the row lock, because under the fix the second thread blocks on
     * that lock and never reaches any later line, so a barrier below it would stall until a lock
     * or rendezvous timeout and fail on correct code. It also has to stay downstream of
     * ensureContentAndGetId, which hashes the content, canonicalizes it (paying classloading and
     * a shared class-initialization lock on first use) and inserts the content row in its own
     * transaction. At method entry all of that would sit between the barrier and the
     * versionOrder read, so one thread could fall far enough behind for the other to finish,
     * which again passes against unfixed code.
     *
     * <p>The two threads submit different content on purpose. Identical content would make them
     * contend on the content unique constraint inside ensureContentAndGetId, adding a second
     * serialization point exactly where the barrier needs them to move independently.
     *
     * <p>Both threads pass an explicit and distinct version name. With a null name both writers
     * take the first-version branch, which substitutes the literal string "1"
     * (SqlVersionRepository.createArtifactVersionRaw), so a single insert would break UQ_versions_1
     * (groupId, artifactId, version) and UQ_versions_3 (groupId, artifactId, versionOrder) at
     * once and the resulting VersionAlreadyExistsException could not say which. Distinct names
     * keep UQ_versions_1 satisfied, leaving UQ_versions_3 as the only constraint two racing
     * writers can violate.
     *
     * <p>That gives the regression two shapes. Reverting the lock alone leaves UQ_versions_3 in
     * place, so the database rejects the duplicate order and it arrives as
     * VersionAlreadyExistsException, which the catch below turns back into a named assertion.
     * Removing UQ_versions_3 from h2.ddl as well lets both rows be written, and the versionOrder
     * assertion reports 1 and 1. The test database is created fresh from h2.ddl, so the
     * upgrades/110 scripts and db-version play no part in that run.
     *
     * <p>The fix under test is lockArtifactForVersionWrite, an UPDATE on the parent artifacts row
     * taken before the version count.
     *
     * <p>The race runs only against H2, the default @QuarkusTest datasource. PostgreSQL behaves
     * the same way at READ COMMITTED, so it is the production dialect this is expected to
     * reproduce on, but it still has no regression test under this path.
     */
    @Test
    @BMRule(name = "hold both first-version writers at the same line before either inserts",
        targetClass = "io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage",
        targetMethod = "createArtifactVersion",
        targetLocation = "AFTER INVOKE ensureContentAndGetId",
        // Byteman rule text has no imports, so java.lang names are written out in full.
        condition = "java.lang.Thread.currentThread().getName().startsWith(\""
                + RACE_THREAD_PREFIX + "\")",
        // createRendezvous runs on every firing rather than once during setup, because a rule
        // body is the only code that executes on the racing threads. Called with the same id and
        // count it returns false and leaves the existing rendezvous alone, so the second
        // arrival's call is a no-op. A completed rendezvous deletes itself, which is what lets
        // the fixed id be reused across iterations.
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

        // Null content creates the artifact row with NO versions, so both racing threads below
        // create a first version. versionBranches is only read when versionContent is non-null,
        // so pass null rather than a list nothing reads.
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

            // One budget for both gets, so a slow first result cannot grant the second a fresh
            // window and stretch a hung iteration to twice the timeout.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESULT_TIMEOUT_MS);
            try {
                resultA = futureA.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                resultB = futureB.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                // A bare TimeoutException says nothing about which thread hung or whether the
                // barrier was reached, and those are the two things worth knowing.
                AssertionError timeout = new AssertionError("Iteration " + iteration
                        + " timed out after " + RESULT_TIMEOUT_MS + "ms waiting for the racing"
                        + " creates. A done=" + futureA.isDone() + ", B done=" + futureB.isDone()
                        + ", " + arrivalSnapshot(), e);
                // A thread that finished while the other hung may hold the rendezvous timeout,
                // the one message that says which side never reached the barrier.
                attachFailure(timeout, futureA);
                attachFailure(timeout, futureB);
                throw timeout;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof VersionAlreadyExistsException) {
                    // This is the regression, reported by the database rather than by the
                    // assertion further down. createArtifactVersion relabels every unique
                    // violation as VersionAlreadyExistsException using the caller's version name,
                    // so the message names one of the two versions and reads as though the name
                    // collided. It cannot have: the names are distinct and the artifact id is
                    // fresh per iteration, so UQ_versions_1 has nothing to reject.
                    throw new AssertionError("Iteration " + iteration + ": concurrent"
                            + " first-version creates collided on versionOrder. The second insert"
                            + " violated UQ_versions_3 (groupId, artifactId, versionOrder), which"
                            + " means both threads took the same order.", e);
                }
                // Anything else keeps its own type, including the ExecuteException a rendezvous
                // timeout raises. Attach the arrivals, the only evidence of whether the barrier
                // was reached at all, and the other thread's failure: if B died before the
                // barrier, A's rendezvous timeout is only the symptom.
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

        // Asserted after the try/finally, so a failure inside the try propagates its own
        // exception instead of being masked by this one.
        Assertions.assertTrue(drained, "Worker threads did not finish after shutdownNow");

        // The threads really met at the barrier. Without this, a rule that stopped matching would
        // turn the iteration into two sequential creates that pass for the wrong reason. The -1
        // check comes first because it names the specific failure (see PROP_ARRIVAL_PREFIX).
        Assertions.assertNull(arrival(-1),
                "a writer passed the barrier without meeting anyone");
        Assertions.assertEquals("true", arrival(0),
                "first writer should have arrived at the Byteman rendezvous");
        Assertions.assertEquals("true", arrival(1),
                "second writer should have arrived at the Byteman rendezvous");

        // Guard the premise: the explicit names must have survived, otherwise the versions were
        // auto-named and the assertion below is masked by UQ_versions_1 rather than testing
        // versionOrder.
        Assertions.assertEquals(VERSION_A, resultA.getVersion(),
                "Thread A must keep its explicit version name");
        Assertions.assertEquals(VERSION_B, resultB.getVersion(),
                "Thread B must keep its explicit version name");

        // Which thread wins is not fixed, but the two orders must be exactly 1 and 2. A plain
        // assertNotEquals would also accept nonsense like 1 and 5. Set.copyOf rather than Set.of,
        // because Set.of rejects duplicates by throwing, and the duplicate is exactly the failure
        // this test exists to report through the assertion below.
        Assertions.assertEquals(Set.of(1, 2),
                Set.copyOf(List.of(resultA.getVersionOrder(), resultB.getVersionOrder())),
                "Iteration " + iteration + ": concurrent first-version creates must produce"
                        + " versionOrder 1 and 2, but got " + resultA.getVersionOrder() + " and "
                        + resultB.getVersionOrder());
        Assertions.assertEquals(2L, storage.countArtifactVersions(GROUP_ID, artifactId),
                "Both versions must be persisted");
    }

    /** Names the worker threads, which is what arms the Byteman rule. */
    private static ExecutorService raceExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(2,
                r -> new Thread(r, RACE_THREAD_PREFIX + counter.incrementAndGet()));
    }

    /**
     * Drains without letting an interrupt escape the finally block, where it would replace the
     * assertion that is this test's actual result.
     */
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

    /** Submit a task on a thread with an active CDI request scope. */
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
