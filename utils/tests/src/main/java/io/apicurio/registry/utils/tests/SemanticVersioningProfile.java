package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class SemanticVersioningProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.semver.validation.enabled", "true");
        props.put("apicurio.semver.branching.enabled", "true");
        props.put("apicurio.semver.branching.coerce", "true");
        return props;
    }
}
