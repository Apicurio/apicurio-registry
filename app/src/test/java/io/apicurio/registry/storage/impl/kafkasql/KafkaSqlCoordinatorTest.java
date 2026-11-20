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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Every test is bounded; tests that wait on other threads override this.
@Timeout(10)
class KafkaSqlCoordinatorTest {

    /** A ceiling, not a sleep: long enough that no passing test ever hits it. */
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

    @Test
    void testWaitForResponseTimesOut() {
        configuration.responseTimeout = 1;

        UUID uuid = coordinator.createUUID();

        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(uuid));

        assertTrue(exception.getMessage().contains(uuid.toString()));
        assertTrue(exception.getMessage().contains("Timed out waiting for a Kafka Sql response"));
        // Not chained: the error detail shows the root cause message, which must keep the UUID.
        assertNull(exception.getCause());
        assertEquals(1, exception.getSuppressed().length);
        assertTrue(exception.getSuppressed()[0] instanceof TimeoutException);
        assertEquals(0, coordinator.pendingCount());
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
        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void testRuntimeExceptionPropagation() {
        UUID uuid = coordinator.createUUID();

        IllegalArgumentException original = new IllegalArgumentException("test error");
        coordinator.notifyResponse(uuid, original);

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.waitForResponse(uuid));
        assertSame(original, thrown);
        assertEquals(0, coordinator.pendingCount());
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

    /** A forgotten operation fails fast rather than blocking for the response timeout. */
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

        assertEquals(1, coordinator.pendingCount());
        coordinator.notifyResponse(uuid, "still-here");
        assertEquals("still-here", coordinator.waitForResponse(uuid));
    }

    /** First response wins. */
    @Test
    void testSecondNotifyBeforeWaitDoesNotOverwriteFirst() {
        UUID uuid = coordinator.createUUID();

        coordinator.notifyResponse(uuid, "first-response");
        coordinator.notifyResponse(uuid, "second-response");

        assertEquals("first-response", coordinator.waitForResponse(uuid));
        assertEquals(0, coordinator.pendingCount());
    }

    /** A waiter already parked in waitForResponse is woken by a later notifyResponse. */
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

            assertEquals("woken-response", waiter.get(5, TimeUnit.SECONDS));
            assertEquals(0, coordinator.pendingCount());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * An interrupted waiter keeps its interrupt flag. The task reads the flag itself because the
     * executor clears it once the task returns.
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
            assertTrue(flagAfterThrow.get());
            assertNull(thrown.getCause());
            assertTrue(thrown.getSuppressed()[0] instanceof InterruptedException);
            assertTrue(thrown.getMessage().contains(uuid.toString()));
            assertEquals(0, coordinator.pendingCount());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Waits until the waiter is parked in the future's timed get. */
    private static void awaitParked(AtomicReference<Thread> waiterThread) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> {
                    Thread thread = waiterThread.get();
                    assertNotNull(thread, "The waiter task has not started yet");
                    assertEquals(Thread.State.TIMED_WAITING, thread.getState());
                });
    }

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
                assertEquals("result-" + i,
                        waitFutures.get(i).get(RESPONSE_TIMEOUT_MS + 5000L, TimeUnit.MILLISECONDS));
            }
            assertEquals(0, coordinator.pendingCount());
        } finally {
            executor.shutdownNow();
        }
    }
}
