package io.apicurio.registry.noprofile.rest.mcptools;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables experimental features and A2A while keeping MCP tools disabled.
 */
public class McpToolsDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.a2a.enabled", "true",
                "apicurio.mcp-tools.enabled", "false"
        );
    }
}
