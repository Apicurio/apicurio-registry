package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.RuleUtil.printRule;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleConfig;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;
import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "update",
        description = "Update the configuration of an existing artifact rule"
)
public class ArtifactRuleUpdateCommand extends AbstractCommand {

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
            description = "The rule type ({{rule-types}})"
    )
    private String ruleType;

    @Option(
            names = {"-c", "--config"},
            description = "The rule configuration value.%n{{rule-configs}}",
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
        final var registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        final var rule = new io.apicurio.registry.rest.client.models.Rule();
        rule.setConfig(ruleConfig);
        //noinspection ConstantConditions
        final var updatedRule = convert(registryClient.groups().byGroupId(resolvedGroupId)
                .artifacts().byArtifactId(resolvedArtifactId).rules().byRuleType(ruleType).put(rule));
        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, ruleType, resolvedArtifactId, resolvedGroupId));
            case table -> output.writeStdOutChunk(out -> successMessage(out, ruleType, resolvedArtifactId, resolvedGroupId));
        }
        printRule(output, updatedRule, outputType);
    }

    private static void successMessage(final StringBuilder out, final String ruleType,
                                       final String artifactId, final String groupId) {
        out.append("Rule '").append(ruleType).append("' updated successfully for artifact '")
                .append(artifactId).append("' in group '").append(groupId).append("'.\n");
    }
}
