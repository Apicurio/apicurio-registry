package io.apicurio.registry.noprofile.rest.aicatalog;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables experimental features, A2A, and MCP tools while keeping the AI Catalog
 * and ARD features disabled, to test the feature gates.
 */
public class AiCatalogDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.a2a.enabled", "true",
                "apicurio.mcp-tools.enabled", "true",
                "apicurio.ai-catalog.enabled", "false",
                "apicurio.ard.enabled", "false"
        );
    }
}
