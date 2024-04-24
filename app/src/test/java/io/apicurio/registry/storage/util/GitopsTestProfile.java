package io.apicurio.registry.storage.util;

import io.apicurio.registry.storage.impl.gitops.GitTestRepositoryManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

public class GitopsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.storage.sql.kind", "h2",
                "apicurio.storage.kind", "gitops");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(GitTestRepositoryManager.class));
    }
}
