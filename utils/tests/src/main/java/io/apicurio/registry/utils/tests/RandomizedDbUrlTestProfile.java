package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.UUID;

public class RandomizedDbUrlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.datasource.h2.jdbc.url",
                "jdbc:h2:mem:registry_db".concat(UUID.randomUUID().toString()));
    }
}
