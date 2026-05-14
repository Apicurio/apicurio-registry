package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;

@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete an artifact rule or all artifact rules"
)
public class ArtifactRuleDeleteCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Artifact ID. If not provided, uses the artifactId from the current context."
    )
    private String artifactId;

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "The rule type to delete (VALIDITY, COMPATIBILITY, or INTEGRITY)"
    )
    private String ruleType;

    @Option(
            names = {"--all"},
            description = "Delete all artifact rules.",
            defaultValue = "false"
    )
    private boolean all;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        if (all && ruleType != null) {
            throw new CliException(
                    "Cannot use --all together with a specific rule type. Use either --all or specify a rule type.",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
        if (!all && ruleType == null) {
            throw new CliException(
                    "Please specify a rule type to delete, or use --all to delete all artifact rules.",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
        if (!all) {
            validateRuleType(ruleType);
        }
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            if (all) {
                registryClient.groups().byGroupId(resolvedGroupId)
                        .artifacts().byArtifactId(resolvedArtifactId).rules().delete();
                output.writeStdOutChunk(out -> {
                    out.append("All rules deleted successfully for artifact '")
                            .append(resolvedArtifactId).append("' in group '")
                            .append(resolvedGroupId).append("'.\n");
                });
            } else {
                registryClient.groups().byGroupId(resolvedGroupId)
                        .artifacts().byArtifactId(resolvedArtifactId).rules().byRuleType(ruleType).delete();
                output.writeStdOutChunk(out -> {
                    out.append("Rule '").append(ruleType).append("' deleted successfully for artifact '")
                            .append(resolvedArtifactId).append("' in group '")
                            .append(resolvedGroupId).append("'.\n");
                });
            }
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error deleting rule")
                        .append(ruleType != null ? " '" + ruleType + "'" : "s")
                        .append(" for artifact '")
                        .append(resolvedArtifactId)
                        .append("' in group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
