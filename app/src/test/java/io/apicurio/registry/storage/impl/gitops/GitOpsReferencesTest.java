package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
class GitOpsReferencesTest {

    private static final String GROUP = "com.example.refs";

    @Inject
    @Current
    RegistryStorage storage;

    @BeforeEach
    void setup() {
        final var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/empty");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of()));
    }

    @Test
    void testContentReferences() {
        assertEquals(Set.of(), storage.getArtifactIds(10));

        final var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/smoke03");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("address", "customer")));

        var groups = storage.getGroupIds(10);
        assertEquals(Set.of(GROUP), Set.copyOf(groups));

        var addressVersion = storage.getArtifactVersionContent(GROUP, "address", "1");
        assertNotNull(addressVersion.getGlobalId());
        assertNotNull(addressVersion.getContentId());
        assertNotNull(addressVersion.getContent());

        var customerVersion = storage.getArtifactVersionContent(GROUP, "customer", "1");
        assertNotNull(customerVersion.getGlobalId());
        assertNotNull(customerVersion.getContentId());
        assertNotNull(customerVersion.getContent());

        assertNotNull(customerVersion.getReferences());
        assertEquals(1, customerVersion.getReferences().size());

        ArtifactReferenceDto ref = customerVersion.getReferences().get(0);
        assertEquals(GROUP, ref.getGroupId());
        assertEquals("address", ref.getArtifactId());
        assertEquals("1", ref.getVersion());
        assertEquals("com.example.refs.Address", ref.getName());

        assertNotNull(addressVersion.getReferences());
        assertEquals(0, addressVersion.getReferences().size());

        assertEquals(Set.of(RuleType.VALIDITY),
                Set.copyOf(storage.getArtifactRules(GROUP, "customer")));
        assertEquals("FULL",
                storage.getArtifactRule(GROUP, "customer", RuleType.VALIDITY).getConfiguration());
        assertEquals(Set.of(), Set.copyOf(storage.getArtifactRules(GROUP, "address")));

        var addressMeta = storage.getArtifactMetaData(GROUP, "address");
        assertEquals("Address Schema", addressMeta.getName());
        assertEquals("Reusable address record", addressMeta.getDescription());
        assertEquals("common", addressMeta.getLabels().get("domain"));
        assertEquals("alice", addressMeta.getOwner());

        var customerMeta = storage.getArtifactMetaData(GROUP, "customer");
        assertEquals("Customer Schema", customerMeta.getName());
        assertEquals("Customer record referencing Address", customerMeta.getDescription());
        assertEquals("crm", customerMeta.getLabels().get("domain"));

        var addressVerMeta = storage.getArtifactVersionMetaData(GROUP, "address", "1");
        assertEquals("Address v1", addressVerMeta.getName());
        assertEquals("Initial address schema", addressVerMeta.getDescription());
        assertEquals(Map.of("status", "stable"), addressVerMeta.getLabels());
        assertEquals("alice", addressVerMeta.getOwner());

        var customerVerMeta = storage.getArtifactVersionMetaData(GROUP, "customer", "1");
        assertEquals("Customer v1", customerVerMeta.getName());
        assertEquals("Customer with address reference", customerVerMeta.getDescription());
        assertEquals(Map.of("status", "stable"), customerVerMeta.getLabels());

        var groupMeta = storage.getGroupMetaData(GROUP);
        assertEquals("test-team", groupMeta.getOwner());
        assertEquals("AVRO", groupMeta.getArtifactsType());

        assertEquals(Set.of(RuleType.COMPATIBILITY),
                Set.copyOf(storage.getGroupRules(GROUP)));
        assertEquals("BACKWARD",
                storage.getGroupRule(GROUP, RuleType.COMPATIBILITY).getConfiguration());
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
