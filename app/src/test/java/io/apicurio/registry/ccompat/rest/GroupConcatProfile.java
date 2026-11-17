package io.apicurio.registry.ccompat.rest;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class GroupConcatProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.ccompat.group-concat.enabled", "true");
    }
}
