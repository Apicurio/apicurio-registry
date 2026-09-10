package io.apicurio.registry.cli.globalrule;

import io.apicurio.registry.cli.common.AbstractCommand;
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
        description = "Update the configuration of an existing global rule"
)
public class GlobalRuleUpdateCommand extends AbstractCommand {

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
        validateRuleType(ruleType);
        validateRuleConfig(ruleType, ruleConfig);
        final var rule = new io.apicurio.registry.rest.client.models.Rule();
        rule.setConfig(ruleConfig);
        //noinspection ConstantConditions
        final var updatedRule = convert(client.getRegistryClient().admin().rules().byRuleType(ruleType).put(rule));
        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, ruleType));
            case table -> output.writeStdOutChunk(out -> successMessage(out, ruleType));
        }
        printRule(output, updatedRule, outputType);
    }

    private static void successMessage(final StringBuilder out, final String ruleType) {
        out.append("Global rule '").append(ruleType).append("' updated successfully.\n");
    }
}
