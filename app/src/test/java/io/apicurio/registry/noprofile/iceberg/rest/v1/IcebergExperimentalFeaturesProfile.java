package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Set;

/**
 * Test profile that enables the experimental features gate and Iceberg so that Iceberg endpoints are
 * accessible. It also enables {@link IcebergBadRequestResource}, a test-only endpoint under
 * {@code /apis/iceberg} that always throws, so a test can check how errors raised on an Iceberg path
 * are mapped without starting a separate application.
 */
public class IcebergExperimentalFeaturesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.iceberg.enabled", "true"
        );
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(IcebergBadRequestResource.class);
    }
}
