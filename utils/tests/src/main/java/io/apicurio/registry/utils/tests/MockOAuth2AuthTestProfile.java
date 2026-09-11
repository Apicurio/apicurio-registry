package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight alternative to {@link AuthTestProfile}: same registry auth configuration,
 * but backed by {@link MockOAuth2TestResource} (in-JVM mock-oauth2-server) instead of a
 * Keycloak testcontainer. Use for tests that only need valid OIDC bearer tokens without
 * realm state (pre-created clients, role mappings, etc.). Tests that exercise realm
 * roles or client definitions should still use {@link AuthTestProfile}.
 */
public class MockOAuth2AuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.rest.deletion.group.enabled", "true");
        props.put("apicurio.rest.deletion.artifact.enabled", "true");
        props.put("apicurio.rest.deletion.artifact-version.enabled", "true");
        props.put("apicurio.auth.authenticated-read-access.enabled", "true");
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.owner-only-authorization", "true");
        props.put("apicurio.auth.admin-override.enabled", "true");
        props.put("apicurio.authn.basic-client-credentials.enabled", "true");
        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(MockOAuth2TestResource.class));
    }
}
