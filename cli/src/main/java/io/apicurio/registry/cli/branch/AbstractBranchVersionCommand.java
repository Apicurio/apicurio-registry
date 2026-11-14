package io.apicurio.registry.cli.branch;

import io.apicurio.registry.rest.client.groups.item.artifacts.item.branches.item.versions.VersionsRequestBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Shared base for the {@code acr artifact branch version add|replace} commands. Adds the
 * required branch option and returns the versions request builder for that branch.
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
        return branches(resolvedGroupId, resolvedArtifactId).byBranchId(branchId).versions();
    }
}
