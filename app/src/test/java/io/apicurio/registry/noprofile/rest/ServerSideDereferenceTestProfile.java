package io.apicurio.registry.noprofile.rest;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that enables server-side dereferencing by default.
 */
public class ServerSideDereferenceTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        // Set default reference handling to DEREFERENCE for testing
        props.put("apicurio.rest.artifact.references.default-handling", "DEREFERENCE");
        return props;
    }
}
