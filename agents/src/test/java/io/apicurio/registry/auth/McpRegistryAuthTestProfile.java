package io.apicurio.registry.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test profile that enables the MCP Registry API alongside role-based and owner-only authorization, with
 * two developers so that one can attempt to publish into a server owned by the other.
 */
public class McpRegistryAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>();
        map.put("apicurio.features.experimental.enabled", "true");
        map.put("apicurio.mcp-registry.enabled", "true");

        map.put("quarkus.oidc.tenant-enabled", "false");
        map.put("quarkus.http.auth.basic", "true");
        map.put("apicurio.auth.role-based-authorization", "true");
        map.put("apicurio.auth.owner-only-authorization", "true");
        map.put("quarkus.security.users.embedded.enabled", "true");
        map.put("quarkus.security.users.embedded.plain-text", "true");
        map.put("quarkus.security.users.embedded.users.alice", "alice");
        map.put("quarkus.security.users.embedded.users.bob1", "bob1");
        map.put("quarkus.security.users.embedded.users.carol", "carol");
        map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
        map.put("quarkus.security.users.embedded.roles.bob1", "sr-developer");
        map.put("quarkus.security.users.embedded.roles.carol", "sr-developer");
        return map;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.emptyList();
    }
}
