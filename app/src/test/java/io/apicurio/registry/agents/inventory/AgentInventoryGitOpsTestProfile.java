package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.storage.impl.gitops.GitTestRepositoryManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only gitops storage with inventory sync enabled, as in {@link AgentInventoryTestProfile}.
 */
public class AgentInventoryGitOpsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>(AgentInventoryTestProfile.inventoryOverrides());
        props.put("apicurio.storage.kind", "gitops");
        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(GitTestRepositoryManager.class));
    }
}
