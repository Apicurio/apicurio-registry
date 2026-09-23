package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.impl.polling.SourceMarker;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composite marker tracking the state of all Git repositories in a multi-repo
 * GitOps setup. Each entry maps a repository ID to its current {@link RevCommit}.
 */
class GitOpsMarker implements SourceMarker {

    private final Map<String, RevCommit> commits;

    GitOpsMarker(Map<String, RevCommit> commits) {
        this.commits = Collections.unmodifiableMap(new LinkedHashMap<>(commits));
    }

    Map<String, RevCommit> getCommits() {
        return commits;
    }

    @Override
    public Map<String, String> toSources() {
        var result = new LinkedHashMap<String, String>();
        commits.forEach((id, commit) ->
                result.put(id, commit != null ? commit.name().substring(0, 7) : null));
        return result;
    }

    @Override
    public Instant getCommitTime() {
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
        return toDisplayString();
    }
}
