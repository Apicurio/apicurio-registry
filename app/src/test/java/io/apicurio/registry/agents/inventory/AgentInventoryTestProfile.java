package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.agents.mcpimport.McpImportTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Inventory sync enabled for the namespace {@code team-a}, with Google Cloud Agent Registry served
 * by {@link GoogleAgentRegistryStub} on a fixed loopback port, and the MCP import enabled as in
 * {@link McpImportTestProfile} for {@code importTools}.
 */
public class AgentInventoryTestProfile implements QuarkusTestProfile {

    static final String NAMESPACE = "team-a";

    static Map<String, String> inventoryOverrides() {
        Map<String, String> props = new HashMap<>(McpImportTestProfile.importOverrides());
        props.put("apicurio.agent-inventory.enabled", "true");
        props.put("apicurio.agent-inventory.kubernetes.namespaces", NAMESPACE + ",team-b");
        props.put("apicurio.agent-inventory.google.url", GoogleAgentRegistryStub.url());
        props.put("apicurio.agent-inventory.timeout.seconds", "2");
        props.put("apicurio.agent-inventory.items.max-size", "5");
        // Search results carry no labels at all, so reconciliation must read each artifact's metadata.
        props.put("apicurio.rest.search-results.labels.max-size.bytes", "0");
        // One API request per list call, so the mock server's expectations are exact.
        props.put("quarkus.kubernetes-client.request-retry-backoff-limit", "0");
        return props;
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return inventoryOverrides();
    }
}
