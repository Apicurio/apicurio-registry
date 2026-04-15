package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.impl.polling.AbstractPollingDataSourceManager;
import io.apicurio.registry.storage.impl.polling.PollingDataFile;
import io.apicurio.registry.storage.impl.polling.PollingResult;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads data from one or more local Git repositories on mounted volumes.
 * <p>
 * This manager does NOT fetch or pull from remotes. It expects the Git repositories
 * to be maintained externally (e.g., by a sidecar container or manual setup).
 * <p>
 * For multi-repo setups, files from all repositories are aggregated into a single
 * poll result. Change detection is per-repo — if any repo has a new commit, all
 * repos are re-read and the combined result is returned.
 */
@ApplicationScoped
public class GitManager extends AbstractPollingDataSourceManager<GitOpsMarker> {

    @Inject
    Logger log;

    @Inject
    GitOpsConfig config;

    private final Map<String, GitRepo> sources = new LinkedHashMap<>();

    @Override
    protected Instant getCommitTime(GitOpsMarker marker) {
        return marker.getLatestCommitTime();
    }

    @Override
    public void start() throws IOException {
        start(config);
        for (GitRepoConfig repoConfig : config.getRepos()) {
            var source = new GitRepo(log, repoConfig, config.getWorkspace(), config);
            sources.put(repoConfig.id(), source);
            source.tryOpen();
        }
        log.info("GitOps configured with {} repo(s): {}", sources.size(), sources.keySet());
    }

    @Override
    public PollingResult<GitOpsMarker> poll() throws Exception {
        // Phase 1: Check each source for changes
        Map<String, RevCommit> newCommits = new LinkedHashMap<>();
        for (GitRepo source : sources.values()) {
            RevCommit newCommit = source.checkForChanges();
            if (newCommit != null) {
                newCommits.put(source.getId(), newCommit);
            }
        }

        if (newCommits.isEmpty()) {
            return PollingResult.noChanges(currentMarker());
        }

        // Phase 2: At least one repo changed — collect files from ALL repos
        // (blue-green does full replacement, so we need the complete picture)
        List<PollingDataFile> allFiles = new ArrayList<>();
        List<Runnable> commitActions = new ArrayList<>();
        Map<String, RevCommit> markerCommits = new LinkedHashMap<>();

        for (GitRepo source : sources.values()) {
            // Use the new commit if this source changed, otherwise re-read from last known.
            // Note: during initial startup, repos that haven't loaded yet (previousCommit == null
            // and no new commit) will return null and be skipped. This means the first load may
            // contain data from only a subset of repos. Once all repos have loaded at least once,
            // subsequent changes will always aggregate from all repos.
            RevCommit commit = newCommits.get(source.getId());
            var result = source.collectFiles(commit);
            if (result == null) {
                log.warn("[{}] Could not collect files, skipping", source.getId());
                continue;
            }
            allFiles.addAll(result.files());

            RevCommit resultCommit = result.commit();
            markerCommits.put(source.getId(), resultCommit);
            if (commit != null) {
                // This source changed — register a commit action
                commitActions.add(() -> source.commitMarker(resultCommit));
            }
        }

        var marker = new GitOpsMarker(markerCommits);
        return PollingResult.withChanges(marker, allFiles, () -> commitActions.forEach(Runnable::run));
    }

    /**
     * Builds a marker from the current state of all sources (last known commits).
     */
    private GitOpsMarker currentMarker() {
        Map<String, RevCommit> commits = new LinkedHashMap<>();
        for (GitRepo source : sources.values()) {
            commits.put(source.getId(), source.getPreviousCommit());
        }
        return new GitOpsMarker(commits);
    }

    @PreDestroy
    public void close() {
        sources.values().forEach(GitRepo::close);
    }
}
