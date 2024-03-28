package io.apicurio.registry.storage.impl.gitops;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import lombok.Getter;

import java.util.Map;

public class GitTestRepositoryManager implements QuarkusTestResourceLifecycleManager {

    @Getter
    private static GitTestRepository testRepository;

    @Override
    public Map<String, String> start() {
        testRepository = new GitTestRepository();

        testRepository.initialize();

        return Map.of(
                "registry.gitops.id", "test",
                "registry.gitops.repo.origin.uri", testRepository.getGitRepoUrl(),
                "registry.gitops.repo.origin.branch", testRepository.getGitRepoBranch(),
                "registry.gitops.refresh.every", "5s"
        );
    }


    @Override
    public void stop() {
        try {
            testRepository.close();
            testRepository = null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
