package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.impl.polling.AbstractPollingStorageConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_GITOPS;

@ApplicationScoped
public class GitOpsConfig extends AbstractPollingStorageConfig {

    @ConfigProperty(name = "apicurio.gitops.workspace", defaultValue = "/repos")
    @Info(category = CATEGORY_GITOPS, experimental = true, description = """
            Base directory where Git repositories are mounted. \
            Repository directories are resolved relative to this path.""", availableSince = "3.2.0")
    @Getter
    String workspace;

    @ConfigProperty(name = "apicurio.gitops.repo.dir", defaultValue = "default")
    @Info(category = CATEGORY_GITOPS, experimental = true, description = """
            Directory name of the Git repository, relative to the workspace.""", availableSince = "3.2.0")
    @Getter
    String repoDir;

    @ConfigProperty(name = "apicurio.gitops.repo.branch", defaultValue = "main")
    @Info(category = CATEGORY_GITOPS, experimental = true, description = """
            Branch to read from.""", availableSince = "3.2.0")
    @Getter
    String repoBranch;

    /**
     * Returns the full path to the Git repository by resolving the repo directory
     * against the workspace.
     */
    public Path getRepoPath() {
        return Path.of(workspace).resolve(getRepoDir());
    }

    @Override
    public String getStorageName() {
        return "gitops";
    }
}
