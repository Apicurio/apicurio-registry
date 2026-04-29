package io.apicurio.registry.noprofile.rest.v3;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables the experimental features gate and data contracts
 * so that contract endpoints are accessible.
 */
public class DataContractsEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.contracts.enabled", "true",
                "apicurio.rest.deletion.group.enabled", "true",
                "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true"
        );
    }
}
