package io.apicurio.registry.cli.globalrule;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.RuleUtil.printRule;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;
import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "get",
        description = "Get the configuration of a global rule"
)
public class GlobalRuleGetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The rule type ({{rule-types}})"
    )
    private String ruleType;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        validateRuleType(ruleType);
        //noinspection ConstantConditions
        final var rule = convert(client.getRegistryClient().admin().rules().byRuleType(ruleType).get());
        printRule(output, rule, outputType);
    }
}
