/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
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
        
        Executors.newSingleThreadExecutor().submit(() -> {
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
    }

    @Test
    public void testNullReturnValue() throws Exception {
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofSeconds(5));

        UUID uuid = coordinator.createUUID();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> resultRef = new AtomicReference<>("initial");
        
        Executors.newSingleThreadExecutor().submit(() -> {
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
    }

    @Test
    public void testRuntimeExceptionPropagation() throws Exception {
        when(configuration.getResponseTimeout()).thenReturn(Duration.ofSeconds(5));

        UUID uuid = coordinator.createUUID();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> resultRef = new AtomicReference<>();
        
        Executors.newSingleThreadExecutor().submit(() -> {
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
        
        assertTrue(ex.getMessage().contains("Operation not registered or duplicate response for UUID"));
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
