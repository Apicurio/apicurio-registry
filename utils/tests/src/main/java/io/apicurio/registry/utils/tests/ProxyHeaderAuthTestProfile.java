package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile for proxy header authentication tests.
 *
 * This profile enables proxy header authentication and configures it in strict mode
 * by default. Tests can verify both strict and permissive modes by overriding
 * configuration as needed.
 */
public class ProxyHeaderAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable deletion endpoints for testing
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");

        // Enable proxy header authentication
        props.put("apicurio.authn.proxy-header.enabled", "true");

        // Use permissive mode to allow HTTP auth permission policies to control access
        // In permissive mode, missing headers result in anonymous identity,
        // and the permission policies determine if anonymous access is allowed
        props.put("apicurio.authn.proxy-header.strict", "false");

        // Use default header names (can be overridden in specific tests)
        props.put("apicurio.authn.proxy-header.username", "X-Forwarded-User");
        props.put("apicurio.authn.proxy-header.email", "X-Forwarded-Email");
        props.put("apicurio.authn.proxy-header.groups", "X-Forwarded-Groups");

        // Enable role-based authorization to test group/role mapping
        props.put("apicurio.auth.role-based-authorization", "true");

        // Configure role mappings for testing
        props.put("apicurio.auth.role-source", "token");
        props.put("apicurio.auth.admin-override.enabled", "true");

        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        // Proxy header auth doesn't require Keycloak
        return List.of();
    }
}
