package io.apicurio.registry.storage.util;

import io.apicurio.registry.storage.impl.gitops.GitTestRepositoryManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitopsAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        final Map<String, String> map = new HashMap<>();
        map.put("apicurio.storage.kind", "gitops");
        map.put("apicurio.features.experimental.enabled", "true");
        map.put("apicurio.federation.enabled", "true");
        map.put("quarkus.oidc.tenant-enabled", "false");
        map.put("quarkus.http.auth.basic", "true");
        map.put("apicurio.auth.admin-override.enabled", "true");
        map.put("apicurio.auth.role-based-authorization", "true");
        map.put("apicurio.auth.anonymous-read-access.enabled", "true");
        map.put("quarkus.security.users.embedded.enabled", "true");
        map.put("quarkus.security.users.embedded.plain-text", "true");
        map.put("quarkus.security.users.embedded.users.alice", "alice");
        map.put("quarkus.security.users.embedded.users.duncan", "duncan");
        map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
        map.put("quarkus.security.users.embedded.roles.duncan", "sr-readonly");
        return map;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(GitTestRepositoryManager.class));
    }
}
