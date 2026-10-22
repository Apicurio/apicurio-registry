package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitOpsConfigTest {

    @Test
    void singleRepoIsValid() {
        var repos = List.of(new GitRepoConfig("default", "default", "main"));
        assertDoesNotThrow(() -> GitOpsConfig.validateRepoList(repos));
    }

    @Test
    void multipleReposWithUniqueIdsAreValid() {
        var repos = List.of(
                new GitRepoConfig("repo1", "repo1", "main"),
                new GitRepoConfig("repo2", "repo2", "main"));
        assertDoesNotThrow(() -> GitOpsConfig.validateRepoList(repos));
    }

    @Test
    void sameDirDifferentBranchesAreValid() {
        var repos = List.of(
                new GitRepoConfig("repo-main", "shared", "main"),
                new GitRepoConfig("repo-dev", "shared", "dev"));
        assertDoesNotThrow(() -> GitOpsConfig.validateRepoList(repos));
    }

    @Test
    void duplicateIdIsRejected() {
        var repos = List.of(
                new GitRepoConfig("myrepo", "dir1", "main"),
                new GitRepoConfig("myrepo", "dir2", "main"));
        var ex = assertThrows(ConfigurationException.class,
                () -> GitOpsConfig.validateRepoList(repos));
        assertTrue(ex.getMessage().contains("Duplicate GitOps repository ID: 'myrepo'"));
    }

    @Test
    void duplicateDirAndBranchIsRejected() {
        var repos = List.of(
                new GitRepoConfig("repo1", "shared-dir", "main"),
                new GitRepoConfig("repo2", "shared-dir", "main"));
        var ex = assertThrows(ConfigurationException.class,
                () -> GitOpsConfig.validateRepoList(repos));
        assertTrue(ex.getMessage().contains("Duplicate GitOps repository"));
        assertTrue(ex.getMessage().contains("shared-dir"));
    }

    @Test
    void defaultBranchConsideredInDuplicateCheck() {
        // Both use "main" — one explicitly, one via default in GitRepoConfig
        var repos = List.of(
                new GitRepoConfig("id1", "samedir", "main"),
                new GitRepoConfig("id2", "samedir", "main"));
        assertThrows(ConfigurationException.class,
                () -> GitOpsConfig.validateRepoList(repos));
    }
}
