package io.apicurio.registry.storage.impl.kubernetesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.util.YAMLObjectMapper;
import io.apicurio.registry.storage.util.KubernetesOpsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(KubernetesOpsTestProfile.class)
class KubernetesOpsSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void smokeTest() throws Exception {
        assertEquals(Set.of(), storage.getArtifactIds(10));

        var configMapStore = KubernetesTestResourceManager.getConfigMapStore();

        // Waiting to load smoke01
        configMapStore.load("git/smoke01");
        await().atMost(Duration.ofSeconds(30)).until(() -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("petstore")));

        // Global rules
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("FULL", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());

        // Groups
        assertEquals(Set.of("foo"), Set.copyOf(storage.getGroupIds(10)));

        // Artifact rules
        assertEquals(Set.of(RuleType.COMPATIBILITY), Set.copyOf(storage.getArtifactRules("foo", "petstore")));
        assertEquals("BACKWARD",
                storage.getArtifactRule("foo", "petstore", RuleType.COMPATIBILITY).getConfiguration());

        // Artifact versions
        var version = storage.getArtifactVersionContent("foo", "petstore", "1");
        assertNotNull(version.getGlobalId());
        assertNotNull(version.getContentId());
        var content = loadFile("git/smoke01/content/petstore-1.0.0.yaml");
        assertEquals(YAMLObjectMapper.YAML_MAPPER.readTree(content.bytes()),
                YAMLObjectMapper.YAML_MAPPER.readTree(version.getContent().bytes()));

        // Peers
        var peers = storage.getPeers();
        assertEquals(1, peers.size());
        var peer = peers.get(0);
        assertEquals("eu-registry", peer.getPeerId());
        assertEquals("https://registry.eu.example.com", peer.getUrl());
        assertEquals("EU registry", peer.getName());
        assertTrue(peer.isEnabled());
        assertEquals("eu-registry", peer.getCredentialSecretRef());

        // Waiting to load smoke02
        configMapStore.load("git/smoke02");
        await().atMost(Duration.ofSeconds(30)).until(() -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("person")));

        // Global rules
        assertEquals(Set.of(), Set.copyOf(storage.getGlobalRules()));

        // Groups
        assertEquals(Set.of("bar"), Set.copyOf(storage.getGroupIds(10)));

        // Artifact rules
        assertEquals(Set.of(), Set.copyOf(storage.getArtifactRules("bar", "person")));

        // Artifact versions
        version = storage.getArtifactVersionContent("bar", "person", "1");
        assertNotNull(version.getGlobalId());
        assertNotNull(version.getContentId());
        content = loadFile("git/smoke02/content/Person.json");
        assertEquals(MAPPER.readTree(content.bytes()), MAPPER.readTree(version.getContent().bytes()));

        // Peers removed (omitted/empty peers list wipes the previously loaded peer)
        assertEquals(Set.of(), Set.copyOf(storage.getPeers()));

        // Waiting to load empty
        configMapStore.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).until(() -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of()));
        assertEquals(Set.of(), Set.copyOf(storage.getPeers()));
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }

    private ContentHandle loadFile(String path) {
        try {
            var fullPath = Path.of(
                    requireNonNull(Thread.currentThread().getContextClassLoader().getResource(path)).toURI());
            return ContentHandle.create(Files.readAllBytes(fullPath));
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }
}
