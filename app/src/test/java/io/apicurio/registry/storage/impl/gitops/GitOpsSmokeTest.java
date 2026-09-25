package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.JsonObjectMapper;
import io.apicurio.registry.util.YAMLObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static io.restassured.RestAssured.get;
import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsSmokeTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void smokeTest() throws Exception {
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
        // Verify content matches the source file (YAML content is preserved as YAML)
        var expectedContent = loadFile("git/smoke01/content/petstore-1.0.0.yaml");
        assertEquals(YAMLObjectMapper.YAML_MAPPER.readTree(expectedContent.bytes()),
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

        // Peers removed (omitted/empty peers list wipes the previously loaded peer)
        assertEquals(Set.of(), Set.copyOf(storage.getPeers()));

        // --- Load data without registry config → rejected by safety check, smoke02 data preserved ---
        testRepository.load("git/invalid-content-ref");
        await().pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
            // Previous data should still be served because the failed load does not cause a swap
            assertEquals(Set.of("person"), withContext(() -> storage.getArtifactIds(10)));
        });

        // --- Load data with an invalid peer (reserved id "local") → rejected by PeerValidator,
        // smoke02 data preserved ---
        testRepository.load("git/peers-invalid");

        // Wait for the status endpoint to actually report the rejection of this specific
        // revision. Checking only that the previous data is still served is not enough: that
        // state is already true before the invalid commit is even processed, so a slow poll
        // could pass without ever proving the invalid revision was rejected. The polling status
        // model does not expose a per-error revision/commit id (an ERROR status keeps the
        // *previous successful* sync's source marker, not the rejected one), so the specific
        // error detail below is the strongest available correlation.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"))
                    .body("errors", hasSize(1))
                    .body("errors[0].detail", containsString("Peer id 'local' is reserved."));
        });

        // Previous data should still be served because the failed load does not cause a swap
        assertEquals(Set.of("person"), withContext(() -> storage.getArtifactIds(10)));
        assertEquals(Set.of(), Set.copyOf(storage.getPeers()));

        // Admin writes against this read-only storage are rejected with a 409-mapped exception
        assertThrows(ReadOnlyStorageException.class,
                () -> storage.createPeer(PeerDto.builder().peerId("rejected").url("https://example.com").build()));

        // --- Load empty: Everything cleared (proves the system recovers after invalid data) ---
        testRepository.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(Set.of(), withContext(() -> storage.getArtifactIds(10)));
        });
        assertEquals(Set.of(), Set.copyOf(storage.getPeers()));

        // Still ready (empty is a valid state after initial load)
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertTrue(storage.isReady()));
    }

    @Test
    void validityRuleViolationPreventsLoad() throws Exception {
        assertRuleViolationPreventsLoad("git/rule-violation", "VALIDITY");
    }

    @Test
    void compatibilityRuleViolationPreventsLoad() throws Exception {
        assertRuleViolationPreventsLoad("git/rule-violation-compatibility", "COMPATIBILITY");
    }

    @Test
    void integrityRuleViolationPreventsLoad() throws Exception {
        assertRuleViolationPreventsLoad("git/rule-violation-integrity", "INTEGRITY");
    }

    private void assertRuleViolationPreventsLoad(String violationDataPath, String expectedRuleType)
            throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        // First load valid data
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("petstore"), withContext(() -> storage.getArtifactIds(10)));
        });

        // Load data that violates the rule — should fail validation
        testRepository.load(violationDataPath);
        await().pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
            // Previous data should still be served (validation failure prevents swap)
            assertEquals(Set.of("petstore"), withContext(() -> storage.getArtifactIds(10)));
        });

        // Verify status shows ERROR with the expected rule type
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"))
                    .body("errors", hasSize(1))
                    .body("errors[0].detail", containsString("Rule " + expectedRuleType + " violation"));
        });
    }

    @Test
    void peerReloadUpdatesFieldsAndOmittedEnabledDefaultsToTrue() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/peers-update-1");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var peers = storage.getPeers();
            assertEquals(1, peers.size());
            assertEquals("https://before.example.com", peers.get(0).getUrl());
        });
        var before = storage.getPeers().get(0);
        assertEquals("update-test-peer", before.getPeerId());
        assertEquals("Before Update", before.getName());
        assertFalse(before.isEnabled());
        assertEquals("before-cred", before.getCredentialSecretRef());

        // Reload the same peer id with different field values and enabled omitted entirely.
        testRepository.load("git/peers-update-2");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var peers = storage.getPeers();
            assertEquals(1, peers.size());
            assertEquals("https://after.example.com", peers.get(0).getUrl());
        });
        var after = storage.getPeers().get(0);
        assertEquals("update-test-peer", after.getPeerId());
        assertEquals("After Update", after.getName());
        // enabled is omitted in this load; it must default to true fresh, not carry over the
        // previous load's explicit enabled: false.
        assertTrue(after.isEnabled());
        assertEquals("after-cred", after.getCredentialSecretRef());
    }

    @Test
    void validatedUpToSkipsIncompatibleVersions() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        // Load data with incompatible v1->v2 but validatedUpTo="2" skips that check.
        // Only v2->v3 is validated (v3 adds optional field = backward compatible).
        testRepository.load("git/rule-validated-up-to");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertEquals(Set.of("compat-test"), withContext(() -> storage.getArtifactIds(10)));
        });

        // All 3 versions should be loaded
        var v1 = storage.getArtifactVersionContent("testgroup", "compat-test", "1");
        var v3 = storage.getArtifactVersionContent("testgroup", "compat-test", "3");
        assertNotNull(v1.getContent());
        assertNotNull(v3.getContent());
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
