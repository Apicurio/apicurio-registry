package io.apicurio.registry.cli.globalrule;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
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
            description = "The rule type (VALIDITY, COMPATIBILITY, or INTEGRITY)"
    )
    private String ruleType;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        validateRuleType(ruleType);
        try {
            //noinspection ConstantConditions
            final var rule = convert(client.getRegistryClient().admin().rules().byRuleType(ruleType).get());
            printRule(output, rule, outputType);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error retrieving global rule '")
                        .append(ruleType)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
