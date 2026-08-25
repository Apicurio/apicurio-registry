package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the experimental features gate and the MCP Registry API.
 */
public class McpRegistryExperimentalFeaturesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-registry.enabled", "true"
        );
    }
}
