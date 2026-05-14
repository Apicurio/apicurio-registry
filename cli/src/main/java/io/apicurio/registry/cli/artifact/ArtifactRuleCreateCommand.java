package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.common.RuleUtil.printRule;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleConfig;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;
import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new artifact rule"
)
public class ArtifactRuleCreateCommand extends AbstractCommand {

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
            description = "The rule type (VALIDITY, COMPATIBILITY, or INTEGRITY)"
    )
    private String ruleType;

    @Option(
            names = {"-c", "--config"},
            // NOTE: Keep in sync with RuleUtil.VALID_CONFIGS
            description = "The rule configuration value.%n" +
                    "  VALIDITY: FULL | SYNTAX_ONLY | NONE%n" +
                    "  COMPATIBILITY: BACKWARD | BACKWARD_TRANSITIVE | FORWARD | FORWARD_TRANSITIVE | FULL | FULL_TRANSITIVE | NONE%n" +
                    "  INTEGRITY: FULL | NO_DUPLICATES | REFS_EXIST | ALL_REFS_MAPPED | NO_CIRCULAR_REFERENCES | NONE",
            required = true
    )
    private String ruleConfig;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        validateRuleType(ruleType);
        validateRuleConfig(ruleType, ruleConfig);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            final var newRule = new CreateRule();
            newRule.setRuleType(RuleType.forValue(ruleType));
            newRule.setConfig(ruleConfig);
            registryClient.groups().byGroupId(resolvedGroupId)
                    .artifacts().byArtifactId(resolvedArtifactId).rules().post(newRule);
            switch (outputType.getOutputType()) {
                case json -> output.writeStdErrChunk(out -> successMessage(out, ruleType, resolvedArtifactId, resolvedGroupId));
                case table -> output.writeStdOutChunk(out -> successMessage(out, ruleType, resolvedArtifactId, resolvedGroupId));
            }
            try {
                //noinspection ConstantConditions
                final var rule = convert(registryClient.groups().byGroupId(resolvedGroupId)
                        .artifacts().byArtifactId(resolvedArtifactId).rules().byRuleType(ruleType).get());
                printRule(output, rule, outputType);
            } catch (final ProblemDetails ex) {
                output.writeStdErrChunk(err -> {
                    err.append("Warning: Artifact rule was created but failed to retrieve details: ")
                            .append(ex.getDetail())
                            .append('\n');
                });
            }
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error creating rule '")
                        .append(ruleType)
                        .append("' for artifact '")
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

    private static void successMessage(final StringBuilder out, final String ruleType,
                                       final String artifactId, final String groupId) {
        out.append("Rule '").append(ruleType).append("' created successfully for artifact '")
                .append(artifactId).append("' in group '").append(groupId).append("'.\n");
    }
}
