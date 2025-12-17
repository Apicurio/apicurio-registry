package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for authentication tests with authenticated read access.
 * Uses Quarkus DevServices for automatic Keycloak container management.
 * DevServices configuration is in app/src/test/resources/application.properties
 * under the %authauthenticatedread.* profile prefix.
 */
public class AuthTestProfileAuthenticatedReadAccess implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "authauthenticatedread";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.auth.authenticated-read-access.enabled", "true");

        // Disable DevServices when running cluster tests (external infrastructure)
        if (Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            props.put("quarkus.keycloak.devservices.enabled", "false");
        }
        return props;
    }

}
