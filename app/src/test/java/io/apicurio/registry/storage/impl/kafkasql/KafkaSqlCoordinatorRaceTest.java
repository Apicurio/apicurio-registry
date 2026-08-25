package io.apicurio.registry.storage.impl.kafkasql;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Byteman test: forces notifyResponse during waitForResponse cleanup. Run with -Pbyteman -Dbyteman.script=byteman/coordinator-race.btm */
@EnabledIfSystemProperty(named = "byteman.agent", matches = "true")
@Isolated // Uses JVM-global System.setProperty for Byteman coordination; cannot run in parallel with other tests.
class KafkaSqlCoordinatorRaceTest {

    private KafkaSqlCoordinator coordinator;

    @BeforeEach
    void setup() {
        System.clearProperty("byteman.waiterFrozen");

        KafkaSqlConfiguration configuration = new KafkaSqlConfiguration();
        configuration.responseTimeout = 10000;

        @SuppressWarnings("unchecked")
        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        coordinator = new KafkaSqlCoordinator();
        coordinator.configuration = configurationInstance;
    }

    @AfterEach
    void cleanup() {
        System.clearProperty("byteman.waiterFrozen");
    }

    // Safety net only: prevents the test from blocking the build forever if a bug
    // causes a thread to hang. The test logic itself is deterministic (Byteman
    // freeze+spin); this timeout should never fire under normal conditions.
    @Test
    @Timeout(60)
    void testConcurrentNotifyDuringCleanupDoesNotNPE() throws Exception {
        UUID uuid = coordinator.createUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Object> waiterFuture = executor.submit(() -> {
            coordinator.notifyResponse(uuid, "first-response");
            return coordinator.waitForResponse(uuid);
        });

        Future<?> notifierFuture = executor.submit(() -> {
            try {
                long deadline = System.currentTimeMillis() + 5000;
                while (!"true".equals(System.getProperty("byteman.waiterFrozen"))) {
                    Thread.sleep(50);
                    if (System.currentTimeMillis() > deadline) {
                        throw new AssertionError("Timed out waiting for Byteman rule to fire");
                    }
                }
                coordinator.notifyResponse(uuid, "second-response");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            Object result = waiterFuture.get();
            notifierFuture.get();

            Assertions.assertEquals("true", System.getProperty("byteman.waiterFrozen"),
                    "Byteman rule should have fired");
            Assertions.assertEquals("first-response", result);
        } finally {
            executor.shutdownNow();
        }
    }
}
