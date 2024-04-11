package io.apicurio.registry.rest;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class DisableApisTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("registry.disable.apis","/apis/ccompat/v7/subjects/[^/]+/versions.*,/ui/.*");
        props.put("apicurio.rest.artifact.deletion.enabled", "false");
        return props;
    }

}

