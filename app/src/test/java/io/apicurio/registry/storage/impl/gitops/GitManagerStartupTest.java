package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.impl.polling.PollingDataFile;
import io.apicurio.registry.storage.impl.polling.PollingResult;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Multi-repo startup where the repositories appear on the volume at different times, as they do
 * when a sidecar clones them one after another.
 */
class GitManagerStartupTest {

    @TempDir
    Path workspace;

    private GitManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void loadsAllReposWhenTheyAppearAtDifferentTimes() throws Exception {
        createRepo("repo-a", "a.txt");

        manager = new GitManager();
        manager.log = LoggerFactory.getLogger(GitManager.class);
        manager.config = config(List.of(
                new GitRepoConfig("repo-a", "repo-a", "main"),
                new GitRepoConfig("repo-b", "repo-b", "main")));
        manager.start();

        // repo-b is not there yet: a partial load must not happen.
        assertThat(manager.poll().isHasChanges()).isFalse();

        createRepo("repo-b", "b.txt");

        // Once repo-b appears, both repositories must be loaded together. Before the fix, the aborted
        // poll above had already marked repo-a's commit as reported, so no later poll ever saw a change.
        PollingResult<GitOpsMarker> loaded = null;
        for (int i = 0; i < 3 && loaded == null; i++) {
            var result = manager.poll();
            if (result.isHasChanges()) {
                loaded = result;
            }
        }
        assertThat(loaded).as("a poll should report the changes once both repos exist").isNotNull();
        assertThat(loaded.getFiles()).extracting(PollingDataFile::getPath)
                .contains("a.txt", "b.txt");

        // After the load is committed, nothing changes until a new commit appears.
        loaded.commit();
        assertThat(manager.poll().isHasChanges()).isFalse();
    }

    private void createRepo(String dir, String file) throws Exception {
        var path = Files.createDirectories(workspace.resolve(dir));
        try (var git = Git.init().setDirectory(path.toFile()).setInitialBranch("main").call()) {
            Files.writeString(path.resolve(file), file, StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("add " + file).setSign(false).call();
        }
    }

    private GitOpsConfig config(List<GitRepoConfig> repos) {
        var config = mock(GitOpsConfig.class);
        when(config.getWorkspace()).thenReturn(workspace.toString());
        when(config.getRepos()).thenReturn(repos);
        return config;
    }
}
