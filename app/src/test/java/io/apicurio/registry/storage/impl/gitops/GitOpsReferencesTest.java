package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GitOpsReferencesTest {

    private static final String GROUP = "com.example.refs";

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeAll
    void loadTestData() {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke03");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(Set.of("address", "customer"), storage.getArtifactIds(10));
        });
    }

    @Test
    void testArtifactsAndGroupLoaded() {
        assertEquals(Set.of("address", "customer"), storage.getArtifactIds(10));
        assertEquals(Set.of(GROUP), Set.copyOf(storage.getGroupIds(10)));
    }

    @Test
    void testVersionContentIsLoaded() {
        final var addressVersion = storage.getArtifactVersionContent(GROUP, "address", "1");
        assertTrue(addressVersion.getGlobalId() > 0);
        assertTrue(addressVersion.getContentId() > 0);
        assertNotNull(addressVersion.getContent());
        final String addressContent = new String(addressVersion.getContent().bytes());
        assertTrue(addressContent.contains("\"Address\""),
                "Address schema should contain record name 'Address'");

        final var customerVersion = storage.getArtifactVersionContent(GROUP, "customer", "1");
        assertTrue(customerVersion.getGlobalId() > 0);
        assertTrue(customerVersion.getContentId() > 0);
        assertNotNull(customerVersion.getContent());
        final String customerContent = new String(customerVersion.getContent().bytes());
        assertTrue(customerContent.contains("\"Customer\""),
                "Customer schema should contain record name 'Customer'");
    }

    @Test
    void testContentReferences() {
        final var customerVersion = storage.getArtifactVersionContent(GROUP, "customer", "1");
        assertEquals(1, customerVersion.getReferences().size());

        final ArtifactReferenceDto ref = customerVersion.getReferences().get(0);
        assertEquals(GROUP, ref.getGroupId());
        assertEquals("address", ref.getArtifactId());
        assertEquals("1", ref.getVersion());
        assertEquals("com.example.refs.Address", ref.getName());

        final var addressVersion = storage.getArtifactVersionContent(GROUP, "address", "1");
        assertEquals(0, addressVersion.getReferences().size());
    }

    @Test
    void testArtifactRules() {
        assertEquals(Set.of(RuleType.VALIDITY),
                Set.copyOf(storage.getArtifactRules(GROUP, "customer")));
        assertEquals("FULL",
                storage.getArtifactRule(GROUP, "customer", RuleType.VALIDITY).getConfiguration());
        assertEquals(Set.of(), Set.copyOf(storage.getArtifactRules(GROUP, "address")));
    }

    @Test
    void testArtifactMetadata() {
        final var addressMeta = storage.getArtifactMetaData(GROUP, "address");
        assertEquals("Address Schema", addressMeta.getName());
        assertEquals("Reusable address record", addressMeta.getDescription());
        assertEquals("common", addressMeta.getLabels().get("domain"));
        assertEquals("alice", addressMeta.getOwner());

        final var customerMeta = storage.getArtifactMetaData(GROUP, "customer");
        assertEquals("Customer Schema", customerMeta.getName());
        assertEquals("Customer record referencing Address", customerMeta.getDescription());
        assertEquals("crm", customerMeta.getLabels().get("domain"));
    }

    @Test
    void testVersionMetadata() {
        final var addressVerMeta = storage.getArtifactVersionMetaData(GROUP, "address", "1");
        assertEquals("Address v1", addressVerMeta.getName());
        assertEquals("Initial address schema", addressVerMeta.getDescription());
        assertEquals(Map.of("status", "stable"), addressVerMeta.getLabels());
        assertEquals("alice", addressVerMeta.getOwner());

        final var customerVerMeta = storage.getArtifactVersionMetaData(GROUP, "customer", "1");
        assertEquals("Customer v1", customerVerMeta.getName());
        assertEquals("Customer with address reference", customerVerMeta.getDescription());
        assertEquals(Map.of("status", "stable"), customerVerMeta.getLabels());
    }

    @Test
    void testGroupMetadata() {
        final var groupMeta = storage.getGroupMetaData(GROUP);
        assertEquals("test-team", groupMeta.getOwner());
        assertEquals("AVRO", groupMeta.getArtifactsType());

        assertEquals(Set.of(RuleType.COMPATIBILITY),
                Set.copyOf(storage.getGroupRules(GROUP)));
        assertEquals("BACKWARD",
                storage.getGroupRule(GROUP, RuleType.COMPATIBILITY).getConfiguration());
    }
}
