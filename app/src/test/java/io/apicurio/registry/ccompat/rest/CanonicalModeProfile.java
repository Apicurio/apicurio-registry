package io.apicurio.registry.ccompat.rest;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class CanonicalModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.ccompat.use-canonical-hash", "true");
    }
}
