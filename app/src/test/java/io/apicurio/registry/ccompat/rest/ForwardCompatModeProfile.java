package io.apicurio.registry.ccompat.rest;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ForwardCompatModeProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("registry.rules.global.compatibility", "FORWARD",
            "registry.auth.enabled", "false");
    }
}
