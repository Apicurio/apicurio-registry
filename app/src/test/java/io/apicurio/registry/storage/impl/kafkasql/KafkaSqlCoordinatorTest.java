package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Every test is bounded; the few that wait on other threads override this with a longer budget.
@Timeout(10)
class KafkaSqlCoordinatorTest {

    /**
     * Must outlast the load test's 10s readiness wait, because an early worker's timeout starts
     * before any response is sent. A ceiling, not a sleep, so a passing run pays nothing for it.
     */
    private static final int RESPONSE_TIMEOUT_MS = 30_000;

    private KafkaSqlCoordinator coordinator;

    private KafkaSqlConfiguration configuration;

    @BeforeEach
    void setup() {
        configuration = new KafkaSqlConfiguration();
        configuration.responseTimeout = RESPONSE_TIMEOUT_MS;

        @SuppressWarnings("unchecked")
        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        coordinator = new KafkaSqlCoordinator();
        coordinator.configuration = configurationInstance;
    }

    // The class-level @Timeout makes a get that ignored the configured 1ms and waited the
    // fixture's 30s fail here.
    @Test
    void testWaitForResponseTimesOut() {
        configuration.responseTimeout = 1;

        UUID uuid = coordinator.createUUID();

        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(uuid));

        assertTrue(exception.getMessage().contains(uuid.toString()));
        assertTrue(exception.getMessage().contains("Timed out waiting for a Kafka Sql response"));
        assertNull(exception.getCause(),
                "ProblemDetails.detail renders the root cause only, and chaining the null-message "
                        + "TimeoutException would drop the operation UUID from it");
        assertEquals(0, coordinator.pendingCount(),
                "A timed-out operation must not leave its future in the pending map");
    }

    @Test
    void testSuccessPath() throws Exception {
        UUID uuid = coordinator.createUUID();

        coordinator.notifyResponse(uuid, "test-result");

        Object result = coordinator.waitForResponse(uuid);
        assertEquals("test-result", result);
    }

    @Test
    void testNullReturnValue() throws Exception {
        UUID uuid = coordinator.createUUID();

        coordinator.notifyResponse(uuid, null);

        Object result = coordinator.waitForResponse(uuid);
        assertNull(result);
        assertEquals(0, coordinator.pendingCount(),
                "A null result must be cleaned up like any other; the old dual-map design stored it "
                        + "under a separate marker");
    }

    @Test
    void testRuntimeExceptionPropagation() {
        UUID uuid = coordinator.createUUID();

        IllegalArgumentException original = new IllegalArgumentException("test error");
        coordinator.notifyResponse(uuid, original);

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.waitForResponse(uuid));
        assertSame(original, thrown, "The exception mappers need the original instance, not a copy");
        assertEquals(0, coordinator.pendingCount(),
                "An operation that returned an exception must not leave its future in the "
                        + "pending map");
    }

    @Test
    void testWaitForUnknownUuidThrows() {
        UUID unknown = UUID.randomUUID();
        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(unknown));
        assertTrue(exception.getMessage().contains("No pending operation"));
        assertTrue(exception.getMessage().contains(unknown.toString()));
    }

    @Test
    void testNotifyForNullUuidIsNoOp() {
        coordinator.notifyResponse(null, "value");

        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void testNotifyForUnknownUuidIsNoOp() {
        coordinator.notifyResponse(UUID.randomUUID(), "value");

        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void testCreateUUIDRegistersInPending() {
        assertEquals(0, coordinator.pendingCount());
        coordinator.createUUID();
        assertEquals(1, coordinator.pendingCount());
    }

    @Test
    void testWaitForResponseCleansUpPending() {
        UUID uuid = coordinator.createUUID();
        assertEquals(1, coordinator.pendingCount());
        coordinator.notifyResponse(uuid, "done");
        coordinator.waitForResponse(uuid);
        assertEquals(0, coordinator.pendingCount());
    }

    /**
     * The point of forget: a caller whose message never reached the journal fails fast instead of
     * blocking for the full response timeout. The pendingCount assertion catches a forget that
     * leaves the entry in place. The timeout covers the other way to lose fail-fast: a
     * waitForResponse that recreated a missing entry and waited on it would block for the
     * fixture's 30s before failing.
     */
    @Test
    void testWaitAfterForgetThrowsImmediately() {
        UUID uuid = coordinator.createUUID();
        coordinator.forget(uuid);
        assertEquals(0, coordinator.pendingCount());

        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(uuid));

        assertTrue(exception.getMessage().contains("No pending operation"));
        assertTrue(exception.getMessage().contains(uuid.toString()));
    }

    @Test
    void testForgetUnknownUuidIsNoOp() {
        UUID uuid = coordinator.createUUID();

        coordinator.forget(UUID.randomUUID());

        assertEquals(1, coordinator.pendingCount(),
                "Forgetting an unknown UUID must not disturb other pending operations");
        coordinator.notifyResponse(uuid, "still-here");
        assertEquals("still-here", coordinator.waitForResponse(uuid));
    }

    /**
     * Pins first-response-wins. The Byteman race test cannot: it captures its result before the
     * injected freeze, so it would pass just as happily against a last-write-wins policy. Here
     * both notifications land before anyone waits and the winner is read afterwards, which needs
     * no instrumentation because the ordering is already deterministic.
     */
    @Test
    void testSecondNotifyBeforeWaitDoesNotOverwriteFirst() {
        UUID uuid = coordinator.createUUID();

        coordinator.notifyResponse(uuid, "first-response");
        coordinator.notifyResponse(uuid, "second-response");

        assertEquals("first-response", coordinator.waitForResponse(uuid));
        assertEquals(0, coordinator.pendingCount());
    }

    /**
     * Pins the wakeup path: a waiter already blocked inside the future's get is released by a
     * later notifyResponse and receives exactly that value. It is the only test here that
     * deterministically parks a waiter before notifying: the others either notify first, park
     * only until a timeout, or, like the load test, may or may not park depending on scheduling.
     */
    @Test
    @Timeout(30)
    void testNotifyWakesBlockedWaiter() throws Exception {
        UUID uuid = coordinator.createUUID();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Thread> waiterThread = new AtomicReference<>();
            Future<Object> waiter = executor.submit(() -> {
                waiterThread.set(Thread.currentThread());
                return coordinator.waitForResponse(uuid);
            });

            awaitParked(waiterThread);

            coordinator.notifyResponse(uuid, "woken-response");

            // Far below the fixture's response timeout, so a notify that fails to wake the parked
            // waiter shows up here as a TimeoutException rather than as a slow pass.
            assertEquals("woken-response", waiter.get(5, TimeUnit.SECONDS));
            assertEquals(0, coordinator.pendingCount());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The interrupt branch: a waiter interrupted while parked in the get comes out with its
     * interrupt flag still set and leaves no entry behind. The task reads the flag itself,
     * because the executor clears it once the task returns.
     */
    @Test
    @Timeout(30)
    void testInterruptedWaiterKeepsInterruptFlag() throws Exception {
        UUID uuid = coordinator.createUUID();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Thread> waiterThread = new AtomicReference<>();
            AtomicBoolean flagAfterThrow = new AtomicBoolean();
            Future<RegistryException> waiter = executor.submit(() -> {
                waiterThread.set(Thread.currentThread());
                RegistryException thrown = assertThrows(RegistryException.class,
                        () -> coordinator.waitForResponse(uuid));
                flagAfterThrow.set(Thread.currentThread().isInterrupted());
                return thrown;
            });

            awaitParked(waiterThread);
            waiterThread.get().interrupt();

            RegistryException thrown = waiter.get(5, TimeUnit.SECONDS);
            assertTrue(flagAfterThrow.get(), "The interrupt flag must survive the RegistryException");
            assertInstanceOf(InterruptedException.class, thrown.getCause());
            assertEquals(0, coordinator.pendingCount());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Waits until the waiter is parked inside the future's get. TIMED_WAITING is the state
     * get(timeout) parks in, and the waiter has no other timed wait on its way there.
     */
    private static void awaitParked(AtomicReference<Thread> waiterThread) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> {
                    Thread thread = waiterThread.get();
                    assertNotNull(thread, "The waiter task has not started yet");
                    assertEquals(Thread.State.TIMED_WAITING, thread.getState());
                });
    }

    // A load test, not a race test: the latch proves the 20 workers started, not that they are
    // parked inside the wait, so it cannot pin the wakeup path. testNotifyWakesBlockedWaiter
    // does that. The @Timeout is a safety net that should never fire.
    @Test
    @Timeout(60)
    void testManyConcurrentCreateNotifyWaitCycles() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch waitersReady = new CountDownLatch(threadCount);

            UUID[] uuids = new UUID[threadCount];
            for (int i = 0; i < threadCount; i++) {
                uuids[i] = coordinator.createUUID();
            }

            List<Future<Object>> waitFutures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                UUID uuid = uuids[i];
                waitFutures.add(executor.submit(() -> {
                    waitersReady.countDown();
                    return coordinator.waitForResponse(uuid);
                }));
            }

            assertTrue(waitersReady.await(10, TimeUnit.SECONDS),
                    "All waiter tasks should have started");
            for (int i = 0; i < threadCount; i++) {
                coordinator.notifyResponse(uuids[i], "result-" + i);
            }

            for (int i = 0; i < threadCount; i++) {
                // Bounded past the response timeout, so a worker whose notify was lost fails here
                // with the coordinator's own timeout, wrapped in an ExecutionException, rather
                // than with an opaque interrupt from the @Timeout.
                assertEquals("result-" + i,
                        waitFutures.get(i).get(RESPONSE_TIMEOUT_MS + 5000L, TimeUnit.MILLISECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
