package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.util.KubernetesOpsTestProfile;
import io.apicurio.registry.types.RuleType;
import io.fabric8.kubernetes.client.KubernetesClient;
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
@TestProfile(KubernetesOpsTestProfile.class)
class KubernetesOpsReferencesTest {

    private static final String GROUP = "com.example.refs";

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
    void testContentReferences() throws Exception {
        assertEquals(Set.of(), storage.getArtifactIds(10));

        var configMapStore = KubernetesTestResourceManager.getConfigMapStore();

        // Load smoke03 which contains address + customer (with reference to address)
        configMapStore.load("git/smoke03");
        await().atMost(Duration.ofSeconds(30)).until(
                () -> withContext(() -> storage.getArtifactIds(10)),
                equalTo(Set.of("address", "customer")));

        // Verify group with description and labels
        var groups = storage.getGroupIds(10);
        assertEquals(Set.of(GROUP), Set.copyOf(groups));

        // Verify address artifact metadata
        var addressVersion = storage.getArtifactVersionContent(GROUP, "address", "1");
        assertNotNull(addressVersion.getGlobalId());
        assertNotNull(addressVersion.getContentId());
        assertNotNull(addressVersion.getContent());

        // Verify customer artifact metadata
        var customerVersion = storage.getArtifactVersionContent(GROUP, "customer", "1");
        assertNotNull(customerVersion.getGlobalId());
        assertNotNull(customerVersion.getContentId());
        assertNotNull(customerVersion.getContent());

        // Verify customer has references to address
        assertNotNull(customerVersion.getReferences());
        assertEquals(1, customerVersion.getReferences().size());

        ArtifactReferenceDto ref = customerVersion.getReferences().get(0);
        assertEquals(GROUP, ref.getGroupId());
        assertEquals("address", ref.getArtifactId());
        assertEquals("1", ref.getVersion());
        assertEquals("com.example.refs.Address", ref.getName());

        // Verify address has no references (simple content)
        assertNotNull(addressVersion.getReferences());
        assertEquals(0, addressVersion.getReferences().size());

        // Verify artifact rules
        assertEquals(Set.of(RuleType.VALIDITY),
                Set.copyOf(storage.getArtifactRules(GROUP, "customer")));
        assertEquals("FULL",
                storage.getArtifactRule(GROUP, "customer", RuleType.VALIDITY).getConfiguration());
        assertEquals(Set.of(), Set.copyOf(storage.getArtifactRules(GROUP, "address")));

        // Verify artifact-level metadata (name, description, labels)
        var addressMeta = storage.getArtifactMetaData(GROUP, "address");
        assertEquals("Address Schema", addressMeta.getName());
        assertEquals("Reusable address record", addressMeta.getDescription());
        assertEquals(Map.of("domain", "common"), addressMeta.getLabels());

        var customerMeta = storage.getArtifactMetaData(GROUP, "customer");
        assertEquals("Customer Schema", customerMeta.getName());
        assertEquals("Customer record referencing Address", customerMeta.getDescription());
        assertEquals(Map.of("domain", "crm"), customerMeta.getLabels());

        // Verify version-level metadata (name, description, labels)
        var addressVerMeta = storage.getArtifactVersionMetaData(GROUP, "address", "1");
        assertEquals("Address v1", addressVerMeta.getName());
        assertEquals("Initial address schema", addressVerMeta.getDescription());
        assertEquals(Map.of("status", "stable"), addressVerMeta.getLabels());

        var customerVerMeta = storage.getArtifactVersionMetaData(GROUP, "customer", "1");
        assertEquals("Customer v1", customerVerMeta.getName());
        assertEquals("Customer with address reference", customerVerMeta.getDescription());
        assertEquals(Map.of("status", "stable"), customerVerMeta.getLabels());
    }

    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }
}
