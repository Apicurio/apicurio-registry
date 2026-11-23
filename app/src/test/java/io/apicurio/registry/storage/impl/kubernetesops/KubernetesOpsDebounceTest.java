package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.KubernetesOpsDebounceTestProfile;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * With debouncing on, an updated ConfigMap is loaded once the quiet period has passed, not only after
 * the maximum wait: polls that keep seeing the same pending change must not restart the quiet period.
 */
@QuarkusTest
@TestProfile(KubernetesOpsDebounceTestProfile.class)
class KubernetesOpsDebounceTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    KubernetesClient kubernetesClient;

    @Test
    void updatedConfigMapIsLoadedAfterTheQuietPeriod() {
        KubernetesTestResourceManager.initializeConfigMapStore(kubernetesClient);
        var configMapStore = KubernetesTestResourceManager.getConfigMapStore();

        configMapStore.load("git/smoke01");
        await().atMost(Duration.ofSeconds(30)).until(() -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("petstore")));

        // The same ConfigMaps, with new data
        configMapStore.load("git/smoke02");
        await().atMost(Duration.ofSeconds(30)).until(() -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("person", "user")));
    }

    @ActivateRequestContext
    <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
