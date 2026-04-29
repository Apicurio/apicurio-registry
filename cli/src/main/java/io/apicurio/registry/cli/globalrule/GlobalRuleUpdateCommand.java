package io.apicurio.registry.cli.globalrule;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
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
        name = "update",
        description = "Update the configuration of an existing global rule"
)
public class GlobalRuleUpdateCommand extends AbstractCommand {

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
        validateRuleType(ruleType);
        validateRuleConfig(ruleType, ruleConfig);
        try {
            final var rule = new io.apicurio.registry.rest.client.models.Rule();
            rule.setConfig(ruleConfig);
            //noinspection ConstantConditions
            final var updatedRule = convert(client.getRegistryClient().admin().rules().byRuleType(ruleType).put(rule));
            switch (outputType.getOutputType()) {
                case json -> output.writeStdErrChunk(out -> successMessage(out, ruleType));
                case table -> output.writeStdOutChunk(out -> successMessage(out, ruleType));
            }
            printRule(output, updatedRule, outputType);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error updating global rule '")
                        .append(ruleType)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }

    private static void successMessage(final StringBuilder out, final String ruleType) {
        out.append("Global rule '").append(ruleType).append("' updated successfully.\n");
    }
}
