package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.exception.ConfigurationException;
import io.apicurio.registry.storage.impl.polling.AbstractPollingStorageConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_GITOPS;

@ApplicationScoped
public class GitOpsConfig extends AbstractPollingStorageConfig {

    @ConfigProperty(name = "apicurio.gitops.workspace", defaultValue = "/repos")
    @Info(category = CATEGORY_GITOPS, experimental = true, description = """
            Base directory where Git repositories are mounted. \
            Repository directories are resolved relative to this path.""", availableSince = "3.2.0")
    @Getter
    String workspace;

    // Single-repo shorthand properties (used when apicurio.gitops.repos.N.* is not configured).
    // Optional so we can detect when the user explicitly sets them (vs using defaults).
    @ConfigProperty(name = "apicurio.gitops.repo.dir")
    @Info(category = CATEGORY_GITOPS, experimental = true, description = """
            Directory name of the Git repository, relative to the workspace. \
            This is a shorthand for single-repo setups. For multiple repositories, \
            use the indexed apicurio.gitops.repos.N.dir properties instead.""", availableSince = "3.2.0")
    Optional<String> repoDir;

    @ConfigProperty(name = "apicurio.gitops.repo.branch")
    @Info(category = CATEGORY_GITOPS, experimental = true, description = """
            Branch to read from. \
            This is a shorthand for single-repo setups. For multiple repositories, \
            use the indexed apicurio.gitops.repos.N.branch properties instead.""", availableSince = "3.2.0")
    Optional<String> repoBranch;

    private volatile List<GitRepoConfig> repos;

    /**
     * Returns the list of configured Git repositories.
     * Supports two configuration styles:
     * <ul>
     *   <li>Single-repo shorthand: {@code apicurio.gitops.repo.dir} + {@code apicurio.gitops.repo.branch}</li>
     *   <li>Multi-repo indexed: {@code apicurio.gitops.repos.0.dir}, {@code apicurio.gitops.repos.0.branch}, etc.</li>
     * </ul>
     * Using both styles simultaneously is rejected with a {@link ConfigurationException}.
     */
    public List<GitRepoConfig> getRepos() {
        if (repos == null) {
            repos = buildRepoList();
        }
        return repos;
    }

    private List<GitRepoConfig> buildRepoList() {
        var indexed = readIndexedRepos();
        boolean hasShorthand = repoDir.isPresent() || repoBranch.isPresent();

        if (!indexed.isEmpty() && hasShorthand) {
            throw new ConfigurationException(
                    "Cannot use both single-repo shorthand (apicurio.gitops.repo.dir/branch) "
                    + "and multi-repo indexed (apicurio.gitops.repos.N.*) configuration. "
                    + "Use one or the other.");
        }

        List<GitRepoConfig> result;
        if (!indexed.isEmpty()) {
            result = indexed;
        } else {
            // Single-repo shorthand (with defaults)
            result = List.of(new GitRepoConfig(
                    repoDir.orElse("default"),
                    repoDir.orElse("default"),
                    repoBranch.orElse("main")));
        }
        validateRepoList(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Reads indexed repository configuration from MicroProfile Config.
     * Uses dot-separated indexes ({@code apicurio.gitops.repos.0.dir}) which map
     * to environment variables ({@code APICURIO_GITOPS_REPOS_0_DIR}) via SmallRye's
     * standard underscore-to-dot conversion.
     */
    private List<GitRepoConfig> readIndexedRepos() {
        var config = ConfigProvider.getConfig();
        List<GitRepoConfig> result = new ArrayList<>();

        for (int i = 0; ; i++) {
            Optional<String> dir = config.getOptionalValue("apicurio.gitops.repos." + i + ".dir", String.class);
            if (dir.isEmpty()) {
                // Check for gaps: scan ahead to catch skipped indexes (e.g., repos.0, repos.2)
                for (int j = i + 1; j <= i + 10; j++) {
                    Optional<String> ahead = config.getOptionalValue("apicurio.gitops.repos." + j + ".dir", String.class);
                    if (ahead.isPresent()) {
                        throw new ConfigurationException("Missing apicurio.gitops.repos." + i + ".dir but repos."
                                + j + ".dir is set. Indexes must be dense (no gaps).");
                    }
                }
                break;
            }
            String branch = config.getOptionalValue("apicurio.gitops.repos." + i + ".branch", String.class)
                    .orElse("main");
            String id = config.getOptionalValue("apicurio.gitops.repos." + i + ".id", String.class)
                    .orElse(dir.get());
            result.add(new GitRepoConfig(id, dir.get(), branch));
        }

        return result;
    }

    static void validateRepoList(List<GitRepoConfig> repoList) {
        Set<String> ids = new HashSet<>();
        Set<String> dirBranches = new HashSet<>();
        for (GitRepoConfig repo : repoList) {
            if (!ids.add(repo.id())) {
                throw new ConfigurationException("Duplicate GitOps repository ID: '" + repo.id()
                        + "'. Each repository must have a unique ID.");
            }
            String dirBranch = repo.dir() + ":" + repo.branch();
            if (!dirBranches.add(dirBranch)) {
                throw new ConfigurationException("Duplicate GitOps repository: dir='" + repo.dir()
                        + "', branch='" + repo.branch()
                        + "'. The same directory and branch combination cannot be configured twice.");
            }
        }
    }

    @Override
    public String getStorageName() {
        return "gitops";
    }
}
