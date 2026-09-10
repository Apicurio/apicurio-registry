package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Run with: ./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=ConcurrentVersionCreationTest */
@QuarkusTest
@WithByteman
@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")
public class ConcurrentVersionCreationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentVersionCreationTest.class);

    private static final String OPENAPI_V1 = """
            {"openapi": "3.0.2", "info": {"title": "Race V1", "version": "1.0.0"}}""";

    private static final String OPENAPI_V2 = """
            {"openapi": "3.0.2", "info": {"title": "Race V2", "version": "1.0.1"}}""";

    private static final String OPENAPI_V3 = """
            {"openapi": "3.0.2", "info": {"title": "Race V3", "version": "1.0.2"}}""";

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeEach
    @AfterEach
    void clearBytemanState() {
        System.clearProperty("byteman.writerFrozen");
        System.clearProperty("byteman.writerReleased");
        System.clearProperty("byteman.writerResumedReleased");
        System.clearProperty("byteman.raceTestReady");
    }

    /**
     * Verifies that concurrent version creation produces distinct versionOrder values.
     *
     * The freeze point is inside the transaction, after the artifacts-row lock and the
     * versionOrder read, but before the INSERT. This reproduces the real race window:
     * thread A holds the lock and has computed isFirstVersion; thread B then enters
     * createArtifactVersion and signals thread A to continue. Thread B blocks on the
     * artifacts-row lock until A commits, then reads the updated MAX(versionOrder).
     */
    @Test
    @BMRules(rules = {
        @BMRule(name = "freeze first writer after versionOrder read",
            targetClass = "io.apicurio.registry.storage.impl.sql.repositories.SqlVersionRepository",
            targetMethod = "createArtifactVersionRaw",
            targetLocation = "AT ENTRY",
            condition = "\"true\".equals(java.lang.System.getProperty(\"byteman.raceTestReady\")) AND NOT flagged(\"ConcurrentVersionCreationTest.writer-entered\")",
            action = "flag(\"ConcurrentVersionCreationTest.writer-entered\"); java.lang.System.setProperty(\"byteman.writerFrozen\", \"true\"); waitFor(\"ConcurrentVersionCreationTest.versionOrder-race\", 10000); java.lang.System.setProperty(\"byteman.writerResumedReleased\", java.lang.String.valueOf(java.lang.System.getProperty(\"byteman.writerReleased\")))"),
        @BMRule(name = "release frozen writer when second thread enters",
            targetClass = "io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage",
            targetMethod = "createArtifactVersion(String, String, String, String, ContentWrapperDto, EditableVersionMetaDataDto, java.util.List, boolean, boolean, String)",
            targetLocation = "AT ENTRY",
            condition = "flagged(\"ConcurrentVersionCreationTest.writer-entered\") AND NOT flagged(\"ConcurrentVersionCreationTest.writer-released\")",
            action = "flag(\"ConcurrentVersionCreationTest.writer-released\"); java.lang.System.setProperty(\"byteman.writerReleased\", \"true\"); signalWake(\"ConcurrentVersionCreationTest.versionOrder-race\", true)")
    })
    public void testConcurrentVersionCreationGetsDifferentVersionOrder() throws Exception {
        String groupId = "ConcurrentVersionCreationTest";
        String artifactId = "testConcurrentVersionOrder-" + UUID.randomUUID();

        // Create the artifact with its first version (versionOrder = 1)
        storage.createArtifact(groupId, artifactId, ArtifactType.OPENAPI, null, null,
                ContentWrapperDto.builder()
                        .contentType(ContentTypes.APPLICATION_JSON)
                        .content(ContentHandle.create(OPENAPI_V1))
                        .build(),
                null, Collections.emptyList(), false, false, null);

        // Arm the Byteman rules only after the initial artifact is created.
        // This prevents the freeze rule from firing during createArtifact's
        // internal call to SqlVersionRepository.createArtifactVersionRaw.
        System.setProperty("byteman.raceTestReady", "true");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread A: enters createArtifactVersion, acquires the artifacts-row lock,
        // reads versionOrder, then Byteman freezes it inside SqlVersionRepository
        // before the INSERT.
        Future<ArtifactVersionMetaDataDto> futureA = submitInRequestScope(executor,
                () -> storage.createArtifactVersion(
                        groupId, artifactId, null, ArtifactType.OPENAPI,
                        ContentWrapperDto.builder()
                                .contentType(ContentTypes.APPLICATION_JSON)
                                .content(ContentHandle.create(OPENAPI_V2))
                                .build(),
                        null, Collections.emptyList(), false, false, null));

        // Thread B: spin until Thread A is frozen, then enter createArtifactVersion.
        // Byteman rule 2 fires at Thread B's ENTRY, signaling Thread A to continue.
        // Thread B then proceeds into the transaction, where it blocks on the
        // artifacts-row lock until Thread A commits.
        Future<ArtifactVersionMetaDataDto> futureB = submitInRequestScope(executor, () -> {
            // Both workers start together, so this budget also covers thread A's cold path into
            // the storage layer. It matches rule 1's waitFor timeout; the future ceiling below is
            // sized to clear both of them in sequence. nanoTime because an NTP step must not trip
            // a deadline.
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!"true".equals(System.getProperty("byteman.writerFrozen"))) {
                Thread.sleep(50);
                if (System.nanoTime() - deadlineNanos > 0) {
                    throw new AssertionError("Timed out waiting for the Byteman freeze rule to fire");
                }
            }
            // No sleep is needed between observing the flag and making the call. Rule 2 signals
            // with mustMeet=true, and that is a rendezvous rather than a notify: if thread A has
            // not reached waitFor yet, Helper.signalWake parks a pre-signalled waiter and blocks
            // thread B until A arrives, so the signal cannot be lost. The spin above is still
            // required, because rule 2's condition tests the flag that rule 1 sets on entry.
            return storage.createArtifactVersion(
                    groupId, artifactId, null, ArtifactType.OPENAPI,
                    ContentWrapperDto.builder()
                            .contentType(ContentTypes.APPLICATION_JSON)
                            .content(ContentHandle.create(OPENAPI_V3))
                            .build(),
                    null, Collections.emptyList(), false, false, null);
        });

        ArtifactVersionMetaDataDto resultA;
        ArtifactVersionMetaDataDto resultB;
        try {
            // If rule 2 never fires, thread A only resumes when its own waitFor times out, so the
            // worst legitimate case is thread B's 10s arm budget followed by rule 1's 10s wait.
            // A 15s ceiling would cut that short and report a bare TimeoutException instead of
            // the rendezvous assertions below, which are what actually name the problem.
            resultA = futureA.get(30, TimeUnit.SECONDS);
            resultB = futureB.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            // The storage layer maps any unique constraint violation to
            // VersionAlreadyExistsException, whose message names the version *string*. Left alone
            // it sends whoever broke the lock looking in the wrong place. Only that cause is the
            // regression: anything else (a Byteman coordination timeout, a CDI failure) is
            // rethrown so it speaks for itself.
            if (ex.getCause() instanceof VersionAlreadyExistsException) {
                throw new AssertionError("Concurrent version creation collided: the database "
                        + "rejected the second row with a unique constraint violation. The storage "
                        + "layer does not report which constraint fired, but on this path it means "
                        + "the two threads computed the same versionOrder, so the artifacts-row "
                        + "lock in createArtifactVersion is not serialising them.", ex);
            }
            throw ex;
        } finally {
            // shutdown() would neither interrupt nor wait, leaving a timed-out worker holding an
            // open transaction and the artifacts-row lock into the next test class. The bounded
            // wait gives a worker blocked in JDBC, where an interrupt does not land, time to
            // finish.
            futureA.cancel(true);
            futureB.cancel(true);
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    // A thread parked in Waiter.waitFor cannot be freed: its Object.wait loop
                    // swallows interrupts. Name the leak rather than let the next test class
                    // inherit it silently.
                    LOGGER.warn("Worker threads did not terminate within 10s; one is still parked "
                            + "in a JDBC call or a Byteman wait");
                }
            } catch (InterruptedException ex) {
                // Restore the flag and return. Throwing from here would mask whatever failure
                // brought us into this block.
                Thread.currentThread().interrupt();
            }
        }

        // Both halves of the rendezvous have to be observable. Helper.waitFor(id, millis) returns
        // void and simply proceeds once the timeout expires, so without these checks a silently
        // dead rule 2, a renamed target method for instance, would leave thread A stalling for
        // ten seconds and the test would still pass.
        Assertions.assertEquals("true", System.getProperty("byteman.writerFrozen"),
                "Byteman freeze rule should have set the writerFrozen flag");
        Assertions.assertEquals("true", System.getProperty("byteman.writerReleased"),
                "Byteman release rule should have fired when thread B entered createArtifactVersion");
        // Rule 2 firing is still not proof that it met a live waiter. Waiter.waiting is never
        // reset and Helper.waitFor does not remove the waiter when it times out, so a signalWake
        // arriving after thread A gave up finds the abandoned waiter, signals it and returns
        // true. Rule 1 records writerReleased at the instant it resumes: the release rule sets
        // that property before it signals, so "true" here means thread B was already inside
        // createArtifactVersion when thread A woke, and the two really did overlap.
        Assertions.assertEquals("true", System.getProperty("byteman.writerResumedReleased"),
                "Thread A resumed without thread B having entered createArtifactVersion, so rule 1 "
                        + "timed out instead of being released and the two threads never overlapped");

        // Both versions must have unique versionOrder values. This catches a regression
        // that slips past the unique constraint, for example if versionOrder allocation
        // changes shape and both threads end up writing rows the database accepts.
        Assertions.assertNotEquals(resultA.getVersionOrder(), resultB.getVersionOrder(),
                "Both versions must have different versionOrder values, but thread A got "
                        + resultA.getVersionOrder() + " and thread B got "
                        + resultB.getVersionOrder());
    }

    /** Submit a task on a thread with an active CDI request scope. */
    private <T> Future<T> submitInRequestScope(ExecutorService executor, Callable<T> task) {
        return executor.submit(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                return task.call();
            } finally {
                requestContext.deactivate();
            }
        });
    }
}
