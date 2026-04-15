package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.impl.polling.PollingDataFile;
import io.apicurio.registry.storage.impl.polling.PollingStorageConfig;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a single Git repository for the GitOps storage.
 * Handles repository lifecycle (open/close), change detection,
 * and file collection from the Git object store.
 */
class GitRepo {

    private final Logger log;
    private final GitRepoConfig repoConfig;
    private final String workspace;
    private final PollingStorageConfig pollingConfig;

    private Repository repository;
    // Last successfully loaded commit (committed after blue-green swap)
    private RevCommit previousCommit;
    // Last commit reported as a change to the debouncer (prevents re-reporting the same commit)
    private RevCommit lastReportedCommit;

    GitRepo(Logger log, GitRepoConfig repoConfig, String workspace, PollingStorageConfig pollingConfig) {
        this.log = log;
        this.repoConfig = repoConfig;
        this.workspace = workspace;
        this.pollingConfig = pollingConfig;
    }

    String getId() {
        return repoConfig.id();
    }

    RevCommit getPreviousCommit() {
        return previousCommit;
    }

    void commitMarker(RevCommit commit) {
        previousCommit = commit;
        lastReportedCommit = null;
    }

    /**
     * Attempts to open the Git repository. Returns true if successful.
     */
    boolean tryOpen() {
        Path repoPath = Path.of(workspace).resolve(repoConfig.dir());

        if (!Files.exists(repoPath)) {
            log.warn("[{}] GitOps repository directory does not exist: {}. "
                    + "Waiting for it to appear on the volume.", repoConfig.id(), repoPath);
            return false;
        }

        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Path gitDir = repoPath.resolve(".git");
            if (Files.isDirectory(gitDir)) {
                builder.setGitDir(gitDir.toFile());
            } else {
                builder.setGitDir(repoPath.toFile());
            }
            repository = builder.setMustExist(true).build();
            log.info("[{}] Opened GitOps repository at {}", repoConfig.id(), repoPath);
            return true;
        } catch (IOException e) {
            log.warn("[{}] GitOps repository at {} is not a valid Git repository yet: {}. "
                    + "Will retry on next poll.", repoConfig.id(), repoPath, e.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the repository has a new commit since the last load.
     * Returns the new commit, or null if unchanged or unavailable.
     */
    RevCommit checkForChanges() {
        if (repository == null) {
            if (!tryOpen()) {
                return null;
            }
        }

        Path repoPath = Path.of(workspace).resolve(repoConfig.dir());
        if (!Files.exists(repoPath)) {
            log.warn("[{}] GitOps repository directory was removed: {}. "
                    + "Closing and waiting for it to reappear.", repoConfig.id(), repoPath);
            close();
            return null;
        }

        try {
            RevCommit head = resolveHead();
            if (head == null || head.equals(previousCommit) || head.equals(lastReportedCommit)) {
                return null;
            }
            lastReportedCommit = head;
            log.info("[{}] Detected change: {} -> {}", repoConfig.id(),
                    previousCommit != null ? previousCommit.name() : "(initial)",
                    head.name());
            return head;
        } catch (IOException e) {
            log.warn("[{}] GitOps repository read failed: {}. "
                    + "Closing and will retry on next poll.", repoConfig.id(), e.getMessage());
            close();
            return null;
        }
    }

    /**
     * Collects all files from the given commit (or the last known commit if null).
     * Returns the files and the commit they were read from, or null if unavailable.
     */
    PollResult collectFiles(RevCommit commit) {
        if (repository == null) {
            return null;
        }

        RevCommit target = commit != null ? commit : previousCommit;
        if (target == null) {
            return null;
        }

        try {
            List<PollingDataFile> files = new ArrayList<>();
            ProcessingState tempState = new ProcessingState(pollingConfig, null);

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(target.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String filePath = treeWalk.getPathString();
                    var objectId = treeWalk.getObjectId(0);

                    String sourceId = repoConfig.id();
                    if (pollingConfig.isMetadataFile(filePath)) {
                        try (InputStream data = repository.getObjectDatabase().open(objectId).openStream()) {
                            files.add(GitDataFile.create(sourceId, tempState, filePath, data));
                        }
                    } else {
                        files.add(GitDataFile.createLazy(sourceId, filePath, repository.getObjectDatabase(), objectId));
                    }
                }
            }

            return new PollResult(target, files);
        } catch (IOException e) {
            log.warn("[{}] Failed to collect files from commit {}: {}",
                    repoConfig.id(), target.name(), e.getMessage());
            close();
            return null;
        }
    }

    private RevCommit resolveHead() throws IOException {
        String branch = repoConfig.branch();
        ObjectId headId = repository.resolve("refs/heads/" + branch);
        if (headId == null) {
            headId = repository.resolve("HEAD");
        }
        if (headId == null) {
            log.debug("[{}] Could not resolve HEAD for branch '{}'. Repository may be empty.",
                    repoConfig.id(), branch);
            return null;
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(headId);
        }
    }

    void close() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    /**
     * Result of polling a single repository.
     */
    record PollResult(RevCommit commit, List<PollingDataFile> files) {
    }
}
