package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.storage.impl.kubernetesops.KubernetesTestResourceManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * kubernetesops storage with the MCP Registry API enabled - used to confirm that a write against this
 * read-only storage backend returns a clean 403, the same as gitops does, since both share
 * {@code AbstractPollingRegistryStorage} / {@code AbstractReadOnlyRegistryStorage} and neither overrides
 * {@code isReadOnly()} or the write methods.
 */
public class McpRegistryKubernetesOpsWriteProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.storage.sql.kind", "h2",
                "apicurio.storage.kind", "kubernetesops",
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-registry.enabled", "true"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(KubernetesTestResourceManager.class));
    }
}
