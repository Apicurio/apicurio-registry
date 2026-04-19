package io.apicurio.registry.storage.impl.gitops;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Composite marker tracking the state of all Git repositories in a multi-repo
 * GitOps setup. Each entry maps a repository ID to its current {@link RevCommit}.
 */
class GitOpsMarker {

    private final Map<String, RevCommit> commits;

    GitOpsMarker(Map<String, RevCommit> commits) {
        this.commits = Collections.unmodifiableMap(new LinkedHashMap<>(commits));
    }

    Map<String, RevCommit> getCommits() {
        return commits;
    }

    /**
     * Returns the most recent commit time across all repositories,
     * for use as a timestamp fallback.
     */
    Instant getLatestCommitTime() {
        long maxSeconds = 0;
        for (RevCommit commit : commits.values()) {
            if (commit != null && commit.getCommitTime() > maxSeconds) {
                maxSeconds = commit.getCommitTime();
            }
        }
        return maxSeconds > 0 ? Instant.ofEpochSecond(maxSeconds) : Instant.now();
    }

    @Override
    public String toString() {
        return commits.entrySet().stream()
                .map(e -> e.getKey() + ":" + (e.getValue() != null ? e.getValue().name().substring(0, 7) : "none"))
                .collect(Collectors.joining(", "));
    }
}
