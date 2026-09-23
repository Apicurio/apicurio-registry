package io.apicurio.registry.noprofile.rest.aicatalog;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the experimental features gate plus the AI Catalog,
 * ARD, A2A, and MCP tools features so that the corresponding well-known endpoints
 * are accessible.
 */
public class AiCatalogEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.a2a.enabled", "true",
                "apicurio.mcp-tools.enabled", "true",
                "apicurio.ai-catalog.enabled", "true",
                "apicurio.ard.enabled", "true"
        );
    }
}
