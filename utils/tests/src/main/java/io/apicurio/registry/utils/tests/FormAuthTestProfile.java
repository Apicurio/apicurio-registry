package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile for form based authentication tests.
 *
 * This profile enables form authentication and configures an embedded
 * identity provider with test users to allow end-to-end testing of the login flow.
 */
public class FormAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable form authentication
        props.put("apicurio.authn.form.enabled", "true");
        props.put("quarkus.http.auth.form.enabled", "true");

        // Enable role-based authorization to test group/role mapping
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.role-source", "token");
        props.put("apicurio.auth.admin-override.enabled", "true");

        // Configure Embedded Identity Provider for testing
        props.put("quarkus.security.users.embedded.enabled", "true");
        props.put("quarkus.security.users.embedded.plain-text", "true");

        // Test users
        props.put("quarkus.security.users.embedded.users.admin", "admin");
        props.put("quarkus.security.users.embedded.roles.admin", "sr-admin");

        props.put("quarkus.security.users.embedded.users.developer", "developer");
        props.put("quarkus.security.users.embedded.roles.developer", "sr-developer");

        props.put("quarkus.security.users.embedded.users.readonly", "readonly");
        props.put("quarkus.security.users.embedded.roles.readonly", "sr-readonly");

        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of();
    }
}
