package io.apicurio.registry.cli.globalrule;

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
        description = "Delete a global rule or all global rules"
)
public class GlobalRuleDeleteCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "The rule type to delete (VALIDITY, COMPATIBILITY, or INTEGRITY)"
    )
    private String ruleType;

    @Option(
            names = {"--all"},
            description = "Delete all global rules.",
            defaultValue = "false"
    )
    private boolean all;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (all && ruleType != null) {
            throw new CliException(
                    "Cannot use --all together with a specific rule type. Use either --all or specify a rule type.",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
        if (!all && ruleType == null) {
            throw new CliException(
                    "Please specify a rule type to delete, or use --all to delete all global rules.",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
        if (all) {
            deleteAllRules(output);
        } else {
            validateRuleType(ruleType);
            deleteSingleRule(output);
        }
    }

    private void deleteAllRules(final OutputBuffer output) {
        try {
            client.getRegistryClient().admin().rules().delete();
            output.writeStdOutChunk(out -> {
                out.append("All global rules deleted successfully.\n");
            });
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error deleting all global rules: ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }

    private void deleteSingleRule(final OutputBuffer output) {
        try {
            client.getRegistryClient().admin().rules().byRuleType(ruleType).delete();
            output.writeStdOutChunk(out -> {
                out.append("Global rule '").append(ruleType).append("' deleted successfully.\n");
            });
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error deleting global rule '")
                        .append(ruleType)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
