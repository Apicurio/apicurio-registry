package io.apicurio.registry.storage.util;

import io.apicurio.registry.storage.impl.gitops.GitMultiRepoTestManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

public class GitopsMultiRepoTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // No experimental features gate: GitOps storage is supported since 3.4.0, so this profile
        // also checks that it starts without the gate.
        return Map.of("apicurio.storage.kind", "gitops");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(GitMultiRepoTestManager.class));
    }
}
