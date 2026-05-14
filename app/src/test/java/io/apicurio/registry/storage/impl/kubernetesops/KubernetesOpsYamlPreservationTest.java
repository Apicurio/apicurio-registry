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
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(KubernetesOpsTestProfile.class)
class KubernetesOpsYamlPreservationTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    KubernetesClient kubernetesClient;

    @BeforeEach
    void setup() {
        KubernetesTestResourceManager.initializeConfigMapStore(kubernetesClient);
        var configMapStore = KubernetesTestResourceManager.getConfigMapStore();
        configMapStore.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of()));
    }

    @Test
    void testYamlContentNotConvertedToJson() throws Exception {
        assertEquals(Set.of(), storage.getArtifactIds(10));

        var configMapStore = KubernetesTestResourceManager.getConfigMapStore();

        // Load smoke04 which contains a YAML OpenAPI spec
        configMapStore.load("git/smoke04");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("hello-api")));

        // Verify the YAML content is preserved as YAML, not converted to JSON
        var version = storage.getArtifactVersionContent("com.example.yaml", "hello-api", "1");
        assertNotNull(version.getContent());

        String contentStr = new String(version.getContent().bytes());
        // YAML content should NOT start with '{' (JSON object)
        assertTrue(!contentStr.trim().startsWith("{"),
                "YAML content should be preserved as YAML, not converted to JSON");
        // Should contain YAML-style key: value
        assertTrue(contentStr.contains("openapi:"),
                "Content should contain YAML key format");
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
