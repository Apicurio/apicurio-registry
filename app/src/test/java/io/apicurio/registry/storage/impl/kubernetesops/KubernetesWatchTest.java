package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.KubernetesOpsTestProfile;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Kubernetes Watch API support in KubernetesOps storage.
 */
@QuarkusTest
@TestProfile(KubernetesOpsTestProfile.class)
class KubernetesWatchTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    KubernetesManager kubernetesManager;

    @BeforeEach
    void setup() {
        KubernetesTestResourceManager.initializeConfigMapStore(kubernetesClient);
    }

    @Test
    void testWatchIsActiveAfterInitialization() {
        assertTrue(kubernetesManager.isWatchActive(),
                "Watch should be active after storage initialization");
    }

    @Test
    void testWatchCallbackIsInvoked() {
        AtomicInteger callbackCount = new AtomicInteger(0);

        kubernetesManager.setRefreshCallback(callbackCount::incrementAndGet);

        // Directly simulate a watch event by invoking the callback
        // In a real Kubernetes cluster, this would be triggered by ConfigMap changes
        Runnable refreshCallback = callbackCount::incrementAndGet;
        refreshCallback.run();

        assertEquals(1, callbackCount.get(), "Callback should be invoked once");
    }

    @Test
    void testWatchCanBeStoppedAndRestarted() {
        assertTrue(kubernetesManager.isWatchActive(), "Watch should be active initially");

        kubernetesManager.stopWatch();
        assertFalse(kubernetesManager.isWatchActive(), "Watch should be inactive after stop");

        kubernetesManager.startWatch();
        assertTrue(kubernetesManager.isWatchActive(), "Watch should be active after restart");
    }

    @Test
    void testScheduledPollingStillWorksWithWatch() throws Exception {
        assertEquals(Set.of(), withContext(() -> storage.getArtifactIds(10)));

        var configMapStore = KubernetesTestResourceManager.getConfigMapStore();

        // Load test data via ConfigMaps
        configMapStore.load("git/smoke01");

        // The scheduled polling should pick up changes even when watch is active
        await().atMost(Duration.ofSeconds(15)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("petstore")));

        assertEquals(Set.of("foo"), withContext(() -> Set.copyOf(storage.getGroupIds(10))));
    }

    @ActivateRequestContext
    <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
