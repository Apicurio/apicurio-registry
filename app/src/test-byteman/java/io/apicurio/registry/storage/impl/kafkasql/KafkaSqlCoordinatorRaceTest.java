package io.apicurio.registry.storage.impl.kafkasql;

import jakarta.enterprise.inject.Instance;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives a notifyResponse into waitForResponse's cleanup window, between the completed
 * get() and the pending.remove() in the finally block, and asserts the window is
 * crash-free and leaves no entry behind. This is a liveness harness rather than a
 * red-on-old pin: the dual-map leak this refactor removed needed notifyResponse's
 * returnValues.put to land after the waiter's finally had cleared both maps, which is
 * later than this window, and that put no longer exists to freeze.
 * <p>
 * A run that never hit the window cannot pass. Nothing sets the waiter-released property
 * except the release rule, whose condition requires the freeze rule to have fired and the
 * waiter not to have resumed, so every way of missing the window leaves that property
 * unset and fails the assertion. The individual budgets below are therefore ceilings on
 * failure diagnosis, not part of the pass criterion.
 * <p>
 * Run with {@code ./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=KafkaSqlCoordinatorRaceTest}.
 * No CI workflow activates {@code -Pbyteman}, so this does not run in the pipeline yet. See
 * the Byteman section of DEVELOPING.md for why the profile exists.
 * Like every class in this source root, it is kept out of the default suite by the profile
 * alone, so there is deliberately no system-property guard that could silently skip the test
 * if the agent setup breaks.
 */
@WithByteman
// Required even with the defaults, or the next Byteman class in the JVM fails its setup.
// See the Byteman section of DEVELOPING.md.
@BMUnitConfig
@Isolated // Uses JVM-global System.setProperty for Byteman coordination; cannot run in parallel with other tests.
class KafkaSqlCoordinatorRaceTest {

    /** Set just before the waiter parks in the injected wait. The notifier polls on it. */
    private static final String PROP_WAITER_FROZEN = "byteman.waiterFrozen";

    /**
     * Byteman flag names. Hoisted because the release rule's condition has to stay in step with
     * the freeze rule's action by hand, and a typo in either would silently stop the rule firing
     * rather than failing loudly. {@code @BMRule} attributes take compile-time constants, so
     * these concatenate in exactly as the property names do.
     */
    private static final String FLAG_WAITER_FROZEN = "waiter-frozen";

    private static final String FLAG_WAITER_RESUMED = "waiter-resumed";

    private static final String FLAG_WAITER_RELEASED = "waiter-released";

    /**
     * The rendezvous key the freeze rule parks on and the release rule wakes. Byteman matches
     * waiter to signaller by this string alone, so a mismatch between the two rules would leave
     * the waiter parked for the whole freeze budget with no error anywhere.
     */
    private static final String RENDEZVOUS_KEY = "coordinator-race";

    /**
     * The pass criterion (see the class javadoc). Written before signalWake, so it records that
     * the second notify ran inside the window, not that the wake landed. Cleared on both sides of
     * the test because a stale "true" would be a false pass.
     */
    private static final String PROP_WAITER_RELEASED = "byteman.waiterReleased";

    /** How long the injected wait holds the waiter inside the cleanup window. */
    private static final int FREEZE_BUDGET_MS = 10_000;

    /** How long the notifier polls for the waiter to reach that window before giving up. */
    private static final int NOTIFIER_ARM_TIMEOUT_MS = 5_000;

    /**
     * The response timeout for the coordinator under test. It never expires on any path here: the
     * waiter notifies itself before it waits, so its future is already complete when
     * waitForResponse runs and the injected freeze happens afterwards, in the finally block.
     */
    private static final int RESPONSE_TIMEOUT_MS = 10_000;

    /**
     * Bounds both worker gets at twice the freeze, with headroom for the notifier's poll and
     * signalWake. It only catches a worker blocked somewhere unexpected: an unreleased waiter still
     * returns once its freeze expires, so a missed window is named by the released-property
     * assertion instead.
     */
    private static final long RESULT_TIMEOUT_MS = 2L * FREEZE_BUDGET_MS;

    /** Past the freeze budget, because a thread parked in the injected wait ignores interrupts. */
    private static final long DRAIN_TIMEOUT_MS = FREEZE_BUDGET_MS + 5_000L;

    /** Above every bounded wait, so a stuck side is named by its own get rather than the @Timeout. */
    private static final long TEST_TIMEOUT_MS = 2 * RESULT_TIMEOUT_MS + DRAIN_TIMEOUT_MS + 5_000L;

    private KafkaSqlCoordinator coordinator;

    @BeforeEach
    void setup() {
        clearCoordinationProperties();

        KafkaSqlConfiguration configuration = new KafkaSqlConfiguration();
        configuration.responseTimeout = RESPONSE_TIMEOUT_MS;

        @SuppressWarnings("unchecked")
        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        coordinator = new KafkaSqlCoordinator();
        coordinator.configuration = configurationInstance;
    }

    @AfterEach
    void cleanup() {
        clearCoordinationProperties();
    }

    private static void clearCoordinationProperties() {
        System.clearProperty(PROP_WAITER_FROZEN);
        System.clearProperty(PROP_WAITER_RELEASED);
    }

    // The Byteman flags need no teardown: uninstalling the last rule runs Helper.deactivated, which
    // clears them. That holds only while this is the class's one rule-bearing method; a second one
    // needs distinct flag names or explicit clearing.
    @Test
    @Timeout(value = TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
    @BMRules(rules = {
        // The location matches because KafkaSqlCoordinator.pending is declared as
        // ConcurrentHashMap rather than Map. Widening that field to the interface would silently
        // stop this rule matching.
        //
        // javac duplicates the finally block, so waitForResponse compiles to two
        // ConcurrentHashMap.remove call sites, one on the normal path and one on the exceptional
        // one. Byteman's default count of 1 picks the first, which is the normal path the waiter
        // takes here. Should that ordering change, or a new remove appear earlier in the method,
        // the freeze moves to a path this test does not exercise and the notifier's arm budget
        // expires.
        @BMRule(name = "freeze waiter in finally before remove",
            targetClass = "io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator",
            targetMethod = "waitForResponse",
            targetLocation = "AT INVOKE java.util.concurrent.ConcurrentHashMap.remove",
            condition = "NOT flagged(\"" + FLAG_WAITER_FROZEN + "\")",
            action = "flag(\"" + FLAG_WAITER_FROZEN + "\");"
                    + "java.lang.System.setProperty(\"" + PROP_WAITER_FROZEN + "\", \"true\");"
                    + "waitFor(\"" + RENDEZVOUS_KEY + "\", " + FREEZE_BUDGET_MS + ");"
                    + "flag(\"" + FLAG_WAITER_RESUMED + "\")"),
        // The waiter-resumed guard is what rules out a pass on timeout-only progress. Byteman's
        // Waiter.waitFor returns normally when its budget expires, throwing only for a killed
        // thread, so the waiter reaches the trailing flag on both the signalled and the expired
        // path. A notify arriving after the waiter has resumed therefore finds that flag set,
        // leaves the acknowledgment property unset, and fails the assertion below.
        //
        // Helper.flag and Helper.flagged share one monitor over the flag set, so the read here
        // cannot miss a resume that already happened. That is a visibility guarantee and not an
        // atomicity one: Byteman evaluates the condition and then runs the action as two steps, so
        // a resume landing between them would still let the action set the property. That run
        // still satisfies the pass criterion: the resume flag is set inside the freeze rule's
        // action, before the waiter reaches its remove, so the second notify had already run to
        // its exit inside the window. Only the wake is wasted, which is why the gap is left alone
        // rather than closed with a second flag.
        @BMRule(name = "release waiter after concurrent notify",
            targetClass = "io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator",
            targetMethod = "notifyResponse",
            targetLocation = "AT EXIT",
            // AT EXIT covers the normal returns and not a throw out of notifyResponse, which this
            // path does not produce.
            //
            // The true second argument to signalWake is required, not incidental. The freeze rule
            // sets the frozen property just before it parks, so the notifier can observe the
            // property and get here while the waiter is still short of the wait. A plain
            // signalWake would be dropped in that gap and the waiter would then sit out the full
            // freeze budget. With true, the signal is registered against the key and the notifier
            // blocks until something waits on it, so it survives either side of the park.
            //
            // The cost is a notifier that cannot be interrupted out of the block. A third notify
            // landing inside the freeze would hang it; the bounded get keeps that diagnosable.
            condition = "flagged(\"" + FLAG_WAITER_FROZEN + "\") "
                    + "AND NOT flagged(\"" + FLAG_WAITER_RESUMED + "\") "
                    + "AND NOT flagged(\"" + FLAG_WAITER_RELEASED + "\")",
            action = "flag(\"" + FLAG_WAITER_RELEASED + "\");"
                    + "java.lang.System.setProperty(\"" + PROP_WAITER_RELEASED + "\", \"true\");"
                    + "signalWake(\"" + RENDEZVOUS_KEY + "\", true)")
    })
    void testNotifyDuringCleanupWindowDoesNotCrashOrDeadlock() throws Exception {
        UUID uuid = coordinator.createUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Assigned in the finally and checked after it, so a failure inside the try is not masked
        // while the drain still runs on every exit path. When the try does fail, the drain result
        // rides along as a suppressed exception: that run is the likeliest to leave workers alive.
        String drainFailure;
        Throwable primaryFailure = null;
        try {
            Future<Object> waiterFuture = executor.submit(() -> {
                coordinator.notifyResponse(uuid, "first-response");
                return coordinator.waitForResponse(uuid);
            });

            Future<?> notifierFuture = executor.submit(() -> {
                await().atMost(Duration.ofMillis(NOTIFIER_ARM_TIMEOUT_MS))
                        .pollInterval(Duration.ofMillis(10))
                        .until(() -> "true".equals(System.getProperty(PROP_WAITER_FROZEN)));
                coordinator.notifyResponse(uuid, "second-response");
            });

            Object result = waiterFuture.get(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            notifierFuture.get(RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            Assertions.assertEquals("true", System.getProperty(PROP_WAITER_RELEASED),
                    "The second notifyResponse did not land inside waitForResponse's cleanup window");
            Assertions.assertEquals("first-response", result);
            // Only a cleanup check. A notifyResponse that wrote the entry back would slip past it,
            // because the write lands inside the window and the waiter's own remove then clears
            // it; testNotifyForUnknownUuidIsNoOp is what pins that notifyResponse never inserts.
            Assertions.assertEquals(0, coordinator.pendingCount(),
                    "notifyResponse left an entry in the pending map");
        } catch (Throwable t) {
            primaryFailure = t;
            throw t;
        } finally {
            executor.shutdownNow();
            drainFailure = drainQuietly(executor);
            if (primaryFailure != null && drainFailure != null) {
                primaryFailure.addSuppressed(new AssertionError(drainFailure));
            }
        }

        // Both workers can outlive shutdownNow: Byteman 4.0.27's Waiter.waitFor and signalWake
        // swallow InterruptedException, leaving a live thread in this fork.
        if (drainFailure != null) {
            Assertions.fail(drainFailure);
        }
    }

    /**
     * Drains without letting an interrupt escape the finally block, where it would replace the
     * assertion that is this test's actual result. Returns null on a clean drain, otherwise the
     * failure message.
     */
    private static String drainQuietly(ExecutorService executor) {
        try {
            return executor.awaitTermination(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    ? null
                    : "Worker threads did not finish after shutdownNow";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "The test thread was interrupted while draining the executor, which means the "
                    + "@Timeout fired rather than a worker refusing to stop";
        }
    }
}
