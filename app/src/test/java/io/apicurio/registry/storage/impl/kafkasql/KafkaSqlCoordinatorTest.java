package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class KafkaSqlCoordinatorTest {

    private KafkaSqlCoordinator coordinator;
    private KafkaSqlConfiguration configuration;

    @BeforeEach
    public void setUp() {
        coordinator = new KafkaSqlCoordinator();
        configuration = Mockito.mock(KafkaSqlConfiguration.class);
        
        @SuppressWarnings("unchecked")
        Instance<KafkaSqlConfiguration> configurationInstance = Mockito.mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);
        
        coordinator.configuration = configurationInstance;
    }

    @Test
    public void testNormalCompletion() throws Exception {
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofSeconds(5));

        UUID uuid = coordinator.createUUID();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> resultRef = new AtomicReference<>();
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    Object res = coordinator.waitForResponse(uuid);
                    resultRef.set(res);
                } catch (Exception e) {
                    resultRef.set(e);
                } finally {
                    latch.countDown();
                }
            });
            
            coordinator.notifyResponse(uuid, "success");
            
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals("success", resultRef.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testNullReturnValue() throws Exception {
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofSeconds(5));

        UUID uuid = coordinator.createUUID();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> resultRef = new AtomicReference<>("initial");
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    Object res = coordinator.waitForResponse(uuid);
                    resultRef.set(res);
                } catch (Exception e) {
                    resultRef.set(e);
                } finally {
                    latch.countDown();
                }
            });
            
            coordinator.notifyResponse(uuid, null);
            
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNull(resultRef.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testRuntimeExceptionPropagation() throws Exception {
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofSeconds(5));

        UUID uuid = coordinator.createUUID();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> resultRef = new AtomicReference<>();
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    coordinator.waitForResponse(uuid);
                } catch (Exception e) {
                    resultRef.set(e);
                } finally {
                    latch.countDown();
                }
            });
            
            IllegalArgumentException expectedException = new IllegalArgumentException("test error");
            coordinator.notifyResponse(uuid, expectedException);
            
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(resultRef.get() instanceof IllegalArgumentException);
            assertEquals("test error", ((IllegalArgumentException) resultRef.get()).getMessage());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testTimeoutPath() {
        // Set a very short timeout
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofMillis(50));

        UUID uuid = coordinator.createUUID();
        
        RegistryException ex = assertThrows(RegistryException.class, () -> {
            coordinator.waitForResponse(uuid);
        });
        
        assertTrue(ex.getMessage().contains("Timeout waiting for a Kafka Sql response"));
        assertTrue(ex.getCause() instanceof TimeoutException);
    }

    @Test
    public void testNotRegisteredPath() {
        UUID unregisteredUuid = UUID.randomUUID();
        
        RegistryException ex = assertThrows(RegistryException.class, () -> {
            coordinator.waitForResponse(unregisteredUuid);
        });
        
        assertTrue(ex.getMessage().contains("Operation not registered for UUID"));
    }

    @Test
    public void testNotifyResponseAfterTimeoutRaceCondition() {
        // Set a very short timeout
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofMillis(50));

        UUID uuid = coordinator.createUUID();
        
        assertThrows(RegistryException.class, () -> {
            coordinator.waitForResponse(uuid);
        });
        
        // After timeout, the HTTP thread's finally block has removed the UUID from the operations map.
        // If the Kafka consumer thread now processes a late response and calls notifyResponse,
        // it must not throw any exceptions (like NullPointerException) on the caller thread.
        assertDoesNotThrow(() -> {
            coordinator.notifyResponse(uuid, "late_response");
        });
    }

}
