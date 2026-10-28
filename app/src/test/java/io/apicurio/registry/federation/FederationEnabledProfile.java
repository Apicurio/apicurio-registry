package io.apicurio.registry.federation;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables the experimental features gate and federation, so the peer management API is served.
 */
public class FederationEnabledProfile implements QuarkusTestProfile {

    static Map<String, String> federationOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.federation.enabled", "true"
        );
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return federationOverrides();
    }
}
