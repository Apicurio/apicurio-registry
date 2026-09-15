package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.branches.BranchesRequestBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Shared base for the {@code acr artifact branch} commands. Holds the group and artifact
 * options, resolves them (falling back to the CLI context), and validates that both exist
 * before returning the branches request builder for the artifact.
 */
@Command
abstract class AbstractBranchCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    protected String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Artifact ID. If not provided, uses the artifactId from the current context."
    )
    protected String artifactId;

    protected String resolvedGroupId() {
        return IdUtil.resolveGroupId(groupId, config);
    }

    protected String resolvedArtifactId() {
        return IdUtil.resolveArtifactId(artifactId, config);
    }

    protected BranchesRequestBuilder branches(final String resolvedGroupId, final String resolvedArtifactId) {
        final RegistryClient registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        IdUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
        return registryClient.groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                .branches();
    }
}
