package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicAuthWithPropertiesTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>();
        map.put("quarkus.oidc.tenant-enabled", "false");
        map.put("quarkus.http.auth.basic", "true");
        map.put("apicurio.auth.role-based-authorization", "true");
        map.put("apicurio.auth.owner-only-authorization", "true");
        map.put("quarkus.security.users.embedded.enabled", "true");
        map.put("quarkus.security.users.embedded.plain-text", "true");
        map.put("quarkus.security.users.embedded.users.alice", "alice");
        map.put("quarkus.security.users.embedded.users.bob1", "bob1");
        map.put("quarkus.security.users.embedded.users.bob2", "bob2");
        map.put("quarkus.security.users.embedded.users.duncan", "duncan");
        map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
        map.put("quarkus.security.users.embedded.roles.bob1", "sr-developer");
        map.put("quarkus.security.users.embedded.roles.bob2", "sr-developer");
        map.put("quarkus.security.users.embedded.roles.duncan", "sr-readonly");
        return map;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.emptyList();
    }
}
