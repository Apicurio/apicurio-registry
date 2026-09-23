package io.apicurio.registry.noprofile.rest.mcptools;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables MCP tools support for testing well-known MCP tools endpoints.
 */
public class McpToolsEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-tools.enabled", "true"
        );
    }
}
