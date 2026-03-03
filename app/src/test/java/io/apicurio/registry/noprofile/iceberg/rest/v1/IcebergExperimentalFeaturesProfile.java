package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the experimental features gate and Iceberg so that Iceberg endpoints are
 * accessible.
 */
public class IcebergExperimentalFeaturesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.iceberg.enabled", "true"
        );
    }
}
