package io.apicurio.registry.rules.defaultglobal;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.Map;

public class DefaultGlobalRulesProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("registry.rules.global.validity","FULL");
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
