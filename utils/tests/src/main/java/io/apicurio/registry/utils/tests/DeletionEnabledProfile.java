package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class DeletionEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifactVersion.enabled", "true");
        return props;
    }
}
