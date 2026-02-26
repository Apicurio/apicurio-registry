package io.apicurio.registry.noprofile.rest.a2a;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the experimental features gate so that A2A endpoints are accessible.
 */
public class ExperimentalFeaturesEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.features.experimental.enabled", "true");
    }
}
