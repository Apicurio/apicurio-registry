package io.apicurio.registry.storage.impl.gitops;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;

public class GitTestRepositoryManager implements QuarkusTestResourceLifecycleManager {

    @Getter
    private static GitTestRepository testRepository;

    @Override
    public Map<String, String> start() {
        testRepository = new GitTestRepository();
        testRepository.initialize();

        Path repoPath = Path.of(testRepository.getGitRepoUrl());

        return Map.of("apicurio.polling-storage.id", "test",
                "apicurio.gitops.workspace", repoPath.getParent().toString(),
                "apicurio.gitops.repo.dir", repoPath.getFileName().toString(),
                "apicurio.gitops.repo.branch", testRepository.getGitRepoBranch(),
                "apicurio.polling-storage.poll-period", "PT1S",
                "apicurio.polling-storage.debounce.quiet-period", "PT0S",
                "apicurio.polling-storage.debounce.max-wait-period", "PT0S");
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
