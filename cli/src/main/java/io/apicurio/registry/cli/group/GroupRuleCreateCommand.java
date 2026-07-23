package io.apicurio.registry.cli.group;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.RuleUtil.printRule;
import static io.apicurio.registry.cli.common.RuleUtil.rejectDefaultGroup;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleConfig;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;
import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new group rule"
)
public class GroupRuleCreateCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context. Group rules are not available for the 'default' group."
    )
    private String groupId;

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
        rejectDefaultGroup(resolvedGroupId);
        validateRuleType(ruleType);
        validateRuleConfig(ruleType, ruleConfig);
        final var registryClient = client.getRegistryClient();
        final var newRule = new CreateRule();
        newRule.setRuleType(RuleType.forValue(ruleType));
        newRule.setConfig(ruleConfig);
        registryClient.groups().byGroupId(resolvedGroupId).rules().post(newRule);
        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, ruleType, resolvedGroupId));
            case table -> output.writeStdOutChunk(out -> successMessage(out, ruleType, resolvedGroupId));
        }
        try {
            //noinspection ConstantConditions
            final var rule = convert(registryClient.groups().byGroupId(resolvedGroupId).rules().byRuleType(ruleType).get());
            printRule(output, rule, outputType);
        } catch (ProblemDetails ex) {
            handleProblemDetails(output, ex);
        }
    }

    private static void successMessage(final StringBuilder out, final String ruleType, final String groupId) {
        out.append("Rule '").append(ruleType).append("' created successfully for group '").append(groupId).append("'.\n");
    }
}
