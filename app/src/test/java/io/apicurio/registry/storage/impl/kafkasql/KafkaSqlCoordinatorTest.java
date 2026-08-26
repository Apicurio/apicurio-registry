package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaSqlCoordinatorTest {

    private KafkaSqlCoordinator coordinator;

    @BeforeEach
    void setup() {
        KafkaSqlConfiguration configuration = new KafkaSqlConfiguration();
        configuration.responseTimeout = 30000;

        @SuppressWarnings("unchecked")
        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        coordinator = new KafkaSqlCoordinator();
        coordinator.configuration = configurationInstance;
    }

    @Test
    void testWaitForResponseTimesOut() {
        KafkaSqlConfiguration shortTimeout = new KafkaSqlConfiguration();
        shortTimeout.responseTimeout = 1;

        @SuppressWarnings("unchecked")
        Instance<KafkaSqlConfiguration> configInstance = mock(Instance.class);
        when(configInstance.get()).thenReturn(shortTimeout);
        coordinator.configuration = configInstance;

        UUID uuid = coordinator.createUUID();

        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(uuid));

        assertTrue(exception.getMessage().contains(uuid.toString()));
        assertTrue(exception.getMessage().contains("Timed out waiting for a Kafka Sql response"));
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
    }

    @Test
    void testRuntimeExceptionPropagation() {
        UUID uuid = coordinator.createUUID();

        coordinator.notifyResponse(uuid, new IllegalArgumentException("test error"));

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.waitForResponse(uuid));
        assertEquals("test error", thrown.getMessage());
    }

    @Test
    void testWaitForUnknownUuidThrows() {
        UUID unknown = UUID.randomUUID();
        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> coordinator.waitForResponse(unknown));
        assertTrue(exception.getMessage().contains("No pending operation"));
    }

    @Test
    void testNotifyForNullUuidIsNoOp() {
        coordinator.notifyResponse(null, "value");
    }

    @Test
    void testNotifyForUnknownUuidIsNoOp() {
        coordinator.notifyResponse(UUID.randomUUID(), "value");
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

    // Safety net only: prevents the test from blocking the build forever if a bug
    // causes a thread to hang. The test logic itself is deterministic (CountDownLatch
    // synchronization); this timeout should never fire under normal conditions.
    @Test
    @Timeout(60)
    void testConcurrentWaitAndNotify() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch waitersReady = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();

            UUID[] uuids = new UUID[threadCount];
            for (int i = 0; i < threadCount; i++) {
                uuids[i] = coordinator.createUUID();
            }

            Future<?>[] waitFutures = new Future<?>[threadCount];
            for (int i = 0; i < threadCount; i++) {
                int idx = i;
                waitFutures[i] = executor.submit(() -> {
                    try {
                        waitersReady.countDown();
                        Object result = coordinator.waitForResponse(uuids[idx]);
                        assertEquals("result-" + idx, result);
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                    }
                });
            }

            waitersReady.await();
            for (int i = 0; i < threadCount; i++) {
                coordinator.notifyResponse(uuids[i], "result-" + i);
            }

            for (int i = 0; i < threadCount; i++) {
                waitFutures[i].get();
            }

            assertNull(error.get(), () -> "Concurrent test failed: " + error.get().getMessage());
        } finally {
            executor.shutdownNow();
        }
    }
}
