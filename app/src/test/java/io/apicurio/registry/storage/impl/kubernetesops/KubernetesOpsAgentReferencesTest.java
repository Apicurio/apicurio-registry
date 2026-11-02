package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.util.KubernetesOpsTestProfile;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(KubernetesOpsTestProfile.class)
class KubernetesOpsAgentReferencesTest {

    private static final String GROUP = "com.example.agents";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    KubernetesClient kubernetesClient;

    @BeforeEach
    void setup() {
        KubernetesTestResourceManager.initializeConfigMapStore(kubernetesClient);
        KubernetesTestResourceManager.getConfigMapStore().load("git/empty");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)), equalTo(Set.of()));
    }

    @Test
    void referenceGraphIsLoadedInOneBatch() {
        // The agent is declared before the tool and server it references.
        KubernetesTestResourceManager.getConfigMapStore().load("git/agent-refs");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("weather-agent", "weather-server", "weather-tool")));

        assertEquals(Set.of("weather-server", "weather-agent"), withContext(() -> artifactIds(
                storage.getInboundArtifactReferences(GROUP, "weather-tool", "1"))));
        assertEquals(Set.of("weather-server", "weather-tool"), withContext(() -> artifactIds(
                storage.getArtifactVersionContent(GROUP, "weather-agent", "1").getReferences())));
    }

    private static Set<String> artifactIds(List<ArtifactReferenceDto> references) {
        return references.stream().map(ArtifactReferenceDto::getArtifactId).collect(Collectors.toSet());
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
