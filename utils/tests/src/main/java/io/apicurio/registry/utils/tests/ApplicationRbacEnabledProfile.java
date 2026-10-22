package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ApplicationRbacEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        // Note: we need to enable these properties so that we can access the role mapping REST API
        // If these are not set, then the role mapping REST API will fail with a 403
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.role-source", "application");
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");
        return props;
    }
}
