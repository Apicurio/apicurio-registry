package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.impl.polling.AbstractPollingDataSourceManager;
import io.apicurio.registry.storage.impl.polling.PollingDataFile;
import io.apicurio.registry.storage.impl.polling.PollingResult;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import java.time.Instant;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * Reads data from a local Git repository on a mounted volume.
 * <p>
 * This manager does NOT fetch or pull from a remote. It expects the Git repository
 * to be maintained externally (e.g., by a sidecar container or manual setup).
 * It reads from the Git object store using a pinned commit SHA, which is safe
 * for concurrent reads even while an external process is pulling new data.
 * <p>
 * Change detection works by comparing the current HEAD SHA with the last loaded SHA.
 */
@ApplicationScoped
public class GitManager extends AbstractPollingDataSourceManager<RevCommit> {

    @Inject
    Logger log;

    @Inject
    GitOpsConfig config;

    private Repository repository;

    private RevCommit previousCommit;

    @Override
    protected Instant getCommitTime(RevCommit marker) {
        return Instant.ofEpochSecond(marker.getCommitTime());
    }

    @Override
    public void start() throws IOException {
        start(config);
        tryOpenRepository();
    }

    private void tryOpenRepository() {
        Path repoPath = config.getRepoPath();

        if (!Files.exists(repoPath)) {
            log.warn("GitOps repository directory does not exist: {}. "
                    + "Waiting for it to appear on the volume.", repoPath);
            return;
        }

        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();

            // Support both bare repos and repos with working tree
            Path gitDir = repoPath.resolve(".git");
            if (Files.isDirectory(gitDir)) {
                builder.setGitDir(gitDir.toFile());
            } else {
                builder.setGitDir(repoPath.toFile());
            }

            repository = builder.setMustExist(true).build();
            log.info("Opened GitOps repository at {}", repoPath);
        } catch (IOException e) {
            log.warn("GitOps repository at {} is not a valid Git repository yet: {}. "
                    + "Will retry on next poll.", repoPath, e.getMessage());
        }
    }

    @Override
    public PollingResult<RevCommit> poll() throws Exception {
        if (repository == null) {
            tryOpenRepository();
            if (repository == null) {
                return PollingResult.noChanges(null);
            }
        }

        // Check if the repository directory still exists (handles deletion/unmount)
        if (!Files.exists(config.getRepoPath())) {
            log.warn("GitOps repository directory was removed: {}. "
                    + "Closing repository and waiting for it to reappear.", config.getRepoPath());
            closeRepository();
            return PollingResult.noChanges(null);
        }

        try {
            return doPoll();
        } catch (IOException e) {
            log.warn("GitOps repository read failed: {}. "
                    + "Closing repository and will retry on next poll.", e.getMessage());
            closeRepository();
            return PollingResult.noChanges(null);
        }
    }

    private PollingResult<RevCommit> doPoll() throws IOException {
        // Resolve HEAD to get the current commit
        String branch = config.getRepoBranch();
        ObjectId headId = repository.resolve("refs/heads/" + branch);
        if (headId == null) {
            // Try resolving HEAD directly (works for bare repos)
            headId = repository.resolve("HEAD");
        }
        if (headId == null) {
            log.debug("Could not resolve HEAD for branch '{}'. Repository may be empty.", branch);
            return PollingResult.noChanges(null);
        }

        RevCommit currentCommit;
        try (RevWalk revWalk = new RevWalk(repository)) {
            currentCommit = revWalk.parseCommit(headId);
        }

        // Check if anything changed since last load
        if (currentCommit.equals(previousCommit)) {
            return PollingResult.noChanges(currentCommit);
        }

        log.info("Detected change: {} -> {}", previousCommit != null ? previousCommit.name() : "(initial)",
                currentCommit.name());

        // Walk the tree at the pinned commit SHA and collect files.
        // Registry metadata files (*.registry.yaml/json) are loaded eagerly and parsed.
        // All other files are loaded lazily — their content is only read from the Git
        // object store when actually accessed (e.g., when referenced as content by an artifact).
        List<PollingDataFile> files = new ArrayList<>();
        ProcessingState tempState = new ProcessingState(config, null);

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(currentCommit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String filePath = treeWalk.getPathString();
                var objectId = treeWalk.getObjectId(0);

                if (config.isMetadataFile(filePath)) {
                    // Eagerly load and parse metadata files
                    try (InputStream data = repository.getObjectDatabase().open(objectId).openStream()) {
                        files.add(GitDataFile.create(tempState, filePath, data));
                    }
                } else {
                    // Lazily reference all other files — content loaded on demand
                    files.add(GitDataFile.createLazy(filePath, repository.getObjectDatabase(), objectId));
                }
            }
        }

        return PollingResult.withChanges(currentCommit, files, () -> previousCommit = currentCommit);
    }

    private void closeRepository() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    @PreDestroy
    public void close() {
        closeRepository();
    }
}
