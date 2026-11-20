package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.storage.impl.gitops.GitTestRepositoryManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only gitops storage with MCP import enabled, as in {@link McpImportTestProfile}.
 */
public class McpImportGitOpsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>(McpImportTestProfile.importOverrides());
        props.putAll(Map.of("apicurio.storage.kind", "gitops"));
        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(GitTestRepositoryManager.class));
    }
}
