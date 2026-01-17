package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile for proxy header authentication with trust-proxy-authorization enabled.
 *
 * This profile tests the scenario where the proxy performs both authentication AND
 * authorization, so local authorization checks are skipped.
 */
public class ProxyHeaderTrustAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();

        // Enable deletion endpoints for testing
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");

        // Enable proxy header authentication
        props.put("apicurio.authn.proxy-header.enabled", "true");

        // Use permissive mode
        props.put("apicurio.authn.proxy-header.strict", "false");

        // CRITICAL: Trust proxy to perform authorization
        props.put("apicurio.authn.proxy-header.trust-proxy-authorization", "true");

        // Use default header names
        props.put("apicurio.authn.proxy-header.username", "X-Forwarded-User");
        props.put("apicurio.authn.proxy-header.email", "X-Forwarded-Email");
        props.put("apicurio.authn.proxy-header.groups", "X-Forwarded-Groups");

        // Enable role-based authorization (which will be bypassed for proxy auth)
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
