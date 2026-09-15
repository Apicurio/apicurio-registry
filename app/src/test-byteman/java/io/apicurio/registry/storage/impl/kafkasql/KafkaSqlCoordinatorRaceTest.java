package io.apicurio.registry.storage.impl.kafkasql;

import jakarta.enterprise.inject.Instance;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
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

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives a notifyResponse into waitForResponse's cleanup window, between the completed
 * get() and the pending.remove() in the finally block, and asserts the window is
 * crash-free, the first completed response wins, and no entry is left behind. This is a
 * liveness harness rather than a red-on-old pin: the dual-map NPE this refactor removed
 * lived between the containsKey and get calls of the old notifyResponse, a structure no
 * rule can freeze now that it no longer exists.
 * Run with {@code ./mvnw test -pl :apicurio-registry-app -Pbyteman -Dtest=KafkaSqlCoordinatorRaceTest}.
 * Like every class in this source root, it is kept out of the default suite by the profile
 * alone, so there is deliberately no system-property guard that could silently skip the test
 * if the agent setup breaks.
 */
@WithByteman
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
    @BMRules(rules = {
        @BMRule(name = "freeze waiter in finally before remove",
            targetClass = "io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator",
            targetMethod = "waitForResponse",
            targetLocation = "AT INVOKE java.util.concurrent.ConcurrentHashMap.remove",
            condition = "NOT flagged(\"waiter-frozen\")",
            action = "flag(\"waiter-frozen\");"
                    + "java.lang.System.setProperty(\"byteman.waiterFrozen\", \"true\");"
                    + "waitFor(\"coordinator-race\", 10000)"),
        @BMRule(name = "release waiter after concurrent notify",
            targetClass = "io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator",
            targetMethod = "notifyResponse",
            targetLocation = "AT EXIT",
            condition = "flagged(\"waiter-frozen\") AND NOT flagged(\"waiter-released\")",
            action = "flag(\"waiter-released\");"
                    + "signalWake(\"coordinator-race\", true)")
    })
    void testConcurrentNotifyDuringCleanupDoesNotNPE() throws Exception {
        UUID uuid = coordinator.createUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Object> waiterFuture = executor.submit(() -> {
            coordinator.notifyResponse(uuid, "first-response");
            return coordinator.waitForResponse(uuid);
        });

        Future<?> notifierFuture = executor.submit(() -> {
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(50))
                    .until(() -> "true".equals(System.getProperty("byteman.waiterFrozen")));
            coordinator.notifyResponse(uuid, "second-response");
        });

        try {
            Object result = waiterFuture.get();
            notifierFuture.get();

            Assertions.assertEquals("true", System.getProperty("byteman.waiterFrozen"),
                    "Byteman rule should have fired");
            Assertions.assertEquals("first-response", result);
            Assertions.assertEquals(0, coordinator.pendingCount(),
                    "The race must not leave a pending entry behind");
        } finally {
            executor.shutdownNow();
        }
    }
}
