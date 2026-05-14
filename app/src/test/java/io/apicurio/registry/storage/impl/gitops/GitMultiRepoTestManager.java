package io.apicurio.registry.storage.impl.gitops;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import lombok.Getter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Test resource manager that creates two Git repositories for multi-repo testing.
 */
public class GitMultiRepoTestManager implements QuarkusTestResourceLifecycleManager {

    @Getter
    private static GitTestRepository repoA;

    @Getter
    private static GitTestRepository repoB;

    @Override
    public Map<String, String> start() {
        repoA = new GitTestRepository();
        repoA.initialize();
        repoB = new GitTestRepository();
        repoB.initialize();

        Path pathA = Path.of(repoA.getGitRepoUrl());
        Path pathB = Path.of(repoB.getGitRepoUrl());

        // Both repos must be under the same workspace directory,
        // so use the common parent as workspace
        String workspace = pathA.getParent().toString();

        Map<String, String> config = new HashMap<>();
        config.put("apicurio.polling-storage.id", "test");
        config.put("apicurio.gitops.workspace", workspace);
        config.put("apicurio.gitops.repos.0.dir", pathA.getFileName().toString());
        config.put("apicurio.gitops.repos.0.branch", repoA.getGitRepoBranch());
        config.put("apicurio.gitops.repos.0.id", "repo-a");
        config.put("apicurio.gitops.repos.1.dir", pathB.getFileName().toString());
        config.put("apicurio.gitops.repos.1.branch", repoB.getGitRepoBranch());
        config.put("apicurio.gitops.repos.1.id", "repo-b");
        config.put("apicurio.polling-storage.poll-period", "PT1S");
        config.put("apicurio.polling-storage.debounce.quiet-period", "PT0S");
        config.put("apicurio.polling-storage.debounce.max-wait-period", "PT0S");
        return config;
    }

    @Override
    public void stop() {
        try {
            if (repoA != null) {
                repoA.close();
                repoA = null;
            }
            if (repoB != null) {
                repoB.close();
                repoB = null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
