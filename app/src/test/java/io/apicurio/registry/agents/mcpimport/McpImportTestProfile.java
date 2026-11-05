package io.apicurio.registry.agents.mcpimport;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * MCP import enabled, allowed to reach only the in-process test server on 127.0.0.1 over plain
 * HTTP, with a short timeout.
 */
public class McpImportTestProfile implements QuarkusTestProfile {

    static Map<String, String> importOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-import.enabled", "true",
                "apicurio.mcp-import.allowed-hosts", "127.0.0.1",
                "apicurio.mcp-import.loopback.enabled", "true",
                "apicurio.mcp-import.insecure-http.enabled", "true",
                "apicurio.mcp-import.timeout.seconds", "2");
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return importOverrides();
    }
}
