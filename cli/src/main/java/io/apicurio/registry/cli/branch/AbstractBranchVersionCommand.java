package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.rest.client.groups.item.artifacts.item.branches.item.versions.VersionsRequestBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Shared base for the {@code acr artifact branch version add|replace} commands. Adds the
 * required branch option and validates the branch before returning its versions request
 * builder.
 */
@Command
abstract class AbstractBranchVersionCommand extends AbstractBranchCommand {

    @Option(
            names = {"-b", "--branch"},
            description = "The branch ID.",
            required = true
    )
    protected String branchId;

    protected VersionsRequestBuilder branchVersions(final String resolvedGroupId, final String resolvedArtifactId) {
        final var branchItem = branches(resolvedGroupId, resolvedArtifactId).byBranchId(branchId);
        IdUtil.validateBranch(client.getRegistryClient(), resolvedGroupId, resolvedArtifactId, branchId);
        return branchItem.versions();
    }
}
