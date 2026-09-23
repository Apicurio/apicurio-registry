package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * The MCP Registry API with {@code apicurio.rest.legacy-error-codes.enabled}, a v2 compatibility switch that
 * makes the global status map answer 409 for a rule violation instead of 400. This API must not change shape
 * because of it.
 */
public class McpRegistryLegacyErrorCodesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-registry.enabled", "true",
                "apicurio.rest.legacy-error-codes.enabled", "true"
        );
    }
}
