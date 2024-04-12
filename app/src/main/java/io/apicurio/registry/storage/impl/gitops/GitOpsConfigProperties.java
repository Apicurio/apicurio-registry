package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GitOpsConfigProperties {

    @ConfigProperty(name = "apicurio.gitops.id")
    @Info(category = "gitops", description = "Identifier of this Registry instance. Only data that references this identifier " +
            "will be loaded.", availableSince = "3.0.0")
    @Getter
    String registryId;

    @ConfigProperty(name = "apicurio.gitops.workdir", defaultValue = "/tmp/apicurio-registry-gitops")
    @Info(category = "gitops", description = "Path to GitOps working directory, which is used to store the local git repository.", availableSince = "3.0.0")
    @Getter
    String workDir;

    @ConfigProperty(name = "apicurio.gitops.repo.origin.uri")
    @Info(category = "gitops", description = "URI of the remote git repository containing data to be loaded.", availableSince = "3.0.0")
    @Getter
    String originRepoURI;

    @ConfigProperty(name = "apicurio.gitops.repo.origin.branch", defaultValue = "main")
    @Info(category = "gitops", description = "Name of the branch in the remote git repository containing data to be loaded.", availableSince = "3.0.0")
    @Getter
    String originRepoBranch;
}
