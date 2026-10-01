package io.apicurio.registry.noprofile.rest.a2a;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the experimental features gate and the OpenAPI-to-Agent-Card
 * auto-generation feature (#7135).
 */
public class OpenApiAgentCardEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.a2a.openapi-integration.enabled", "true"
        );
    }
}
