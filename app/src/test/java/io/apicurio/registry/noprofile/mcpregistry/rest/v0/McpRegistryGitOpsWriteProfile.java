package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.storage.impl.gitops.GitTestRepositoryManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * gitops storage with the MCP Registry API enabled - used to confirm that a write against a read-only
 * storage backend returns a clean 403 rather than an unmapped 500.
 */
public class McpRegistryGitOpsWriteProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.storage.kind", "gitops",
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-registry.enabled", "true"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(GitTestRepositoryManager.class));
    }
}
