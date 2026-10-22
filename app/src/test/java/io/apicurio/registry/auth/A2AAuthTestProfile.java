package io.apicurio.registry.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile that enables A2A experimental features with basic auth and admin override.
 * Used for testing agent visibility and entitlement filtering.
 */
public class A2AAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>();
        // Enable A2A experimental features
        map.put("apicurio.features.experimental.enabled", "true");
        map.put("apicurio.a2a.enabled", "true");

        // Enable basic auth with embedded users
        map.put("quarkus.oidc.tenant-enabled", "false");
        map.put("quarkus.http.auth.basic", "true");
        map.put("apicurio.auth.admin-override.enabled", "true");
        map.put("apicurio.auth.role-based-authorization", "true");
        map.put("apicurio.auth.owner-only-authorization", "true");
        map.put("quarkus.security.users.embedded.enabled", "true");
        map.put("quarkus.security.users.embedded.plain-text", "true");
        map.put("quarkus.security.users.embedded.users.alice", "alice");
        map.put("quarkus.security.users.embedded.users.bob1", "bob1");
        map.put("quarkus.security.users.embedded.users.duncan", "duncan");
        map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
        map.put("quarkus.security.users.embedded.roles.bob1", "sr-developer");
        map.put("quarkus.security.users.embedded.roles.duncan", "sr-readonly");
        map.put("apicurio.rest.deletion.group.enabled", "true");
        map.put("apicurio.rest.deletion.artifact.enabled", "true");
        map.put("apicurio.rest.deletion.artifact-version.enabled", "true");
        return map;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.emptyList();
    }
}
