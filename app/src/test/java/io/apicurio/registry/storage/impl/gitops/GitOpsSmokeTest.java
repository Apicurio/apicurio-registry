package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.JsonObjectMapper;
import io.apicurio.registry.util.YAMLObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
@ResourceLock("blue-green-database")
public class GitOpsSmokeTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void smokeTest() throws Exception {
        // Initially empty
        assertEquals(Set.of(), storage.getArtifactIds(10));

        var testRepository = GitTestRepositoryManager.getTestRepository();

        // --- Load smoke01: OpenAPI artifact with rules ---
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore"), withContext(() -> storage.getArtifactIds(10)));
        });

        // Verify storage is ready after first load
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertTrue(storage.isReady()));

        // Global rules
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("FULL", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());

        // Groups
        assertEquals(Set.of("foo"), Set.copyOf(storage.getGroupIds(10)));
        var groupMeta = storage.getGroupMetaData("foo");
        assertEquals("Test group foo", groupMeta.getDescription());

        // Artifact metadata
        var artifactMeta = storage.getArtifactMetaData("foo", "petstore");
        assertEquals("petstore", artifactMeta.getArtifactId());
        assertEquals("OPENAPI", artifactMeta.getArtifactType());

        // Artifact rules
        assertEquals(Set.of(RuleType.COMPATIBILITY), Set.copyOf(storage.getArtifactRules("foo", "petstore")));
        assertEquals("BACKWARD",
                storage.getArtifactRule("foo", "petstore", RuleType.COMPATIBILITY).getConfiguration());

        // Artifact version content
        var version = storage.getArtifactVersionContent("foo", "petstore", "1");
        assertNotNull(version.getContent());
        assertNotNull(version.getGlobalId());
        assertNotNull(version.getContentId());
        // Verify content matches the source file (YAML is converted to JSON during loading)
        var expectedContent = loadFile("git/smoke01/content/petstore-1.0.0.yaml");
        assertEquals(YAMLObjectMapper.MAPPER.readTree(expectedContent.bytes()),
                JsonObjectMapper.MAPPER.readTree(version.getContent().bytes()));

        // --- Load smoke02: Different artifact, no rules ---
        testRepository.load("git/smoke02");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(Set.of("person"), withContext(() -> storage.getArtifactIds(10)));
        });

        // Global rules cleared
        assertEquals(Set.of(), Set.copyOf(storage.getGlobalRules()));

        // Groups switched
        assertEquals(Set.of("bar"), Set.copyOf(storage.getGroupIds(10)));

        // Artifact rules cleared
        assertEquals(Set.of(), Set.copyOf(storage.getArtifactRules("bar", "person")));

        // Content of new artifact
        version = storage.getArtifactVersionContent("bar", "person", "1");
        assertNotNull(version.getContent());
        var personContent = loadFile("git/smoke02/content/Person.json");
        assertEquals(JsonObjectMapper.MAPPER.readTree(personContent.bytes()),
                JsonObjectMapper.MAPPER.readTree(version.getContent().bytes()));

        // --- Load data without registry config → rejected by safety check, smoke02 data preserved ---
        testRepository.load("git/invalid-content-ref");
        await().pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
            // Previous data should still be served because the failed load does not cause a swap
            assertEquals(Set.of("person"), withContext(() -> storage.getArtifactIds(10)));
        });

        // --- Load empty: Everything cleared (proves the system recovers after invalid data) ---
        testRepository.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(Set.of(), withContext(() -> storage.getArtifactIds(10)));
        });

        // Still ready (empty is a valid state after initial load)
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertTrue(storage.isReady()));
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }

    private ContentHandle loadFile(String path) {
        try {
            var fullPath = Path.of(
                    requireNonNull(Thread.currentThread().getContextClassLoader().getResource(path)).toURI());
            return ContentHandle.create(FileUtils.readFileToByteArray(fullPath.toFile()));
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }
}
