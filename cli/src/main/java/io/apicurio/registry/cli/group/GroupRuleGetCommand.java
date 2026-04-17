package io.apicurio.registry.cli.group;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.artifact.ArtifactUtil;
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
import static io.apicurio.registry.cli.common.RuleUtil.rejectDefaultGroup;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;
import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "get",
        description = "Get the configuration of a group rule"
)
public class GroupRuleGetCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context. Group rules are not available for the 'default' group."
    )
    private String groupId;

    @Parameters(
            index = "0",
            description = "The rule type (VALIDITY, COMPATIBILITY, or INTEGRITY)"
    )
    private String ruleType;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId, config);
        rejectDefaultGroup(resolvedGroupId);
        validateRuleType(ruleType);
        try {
            //noinspection ConstantConditions
            final var rule = convert(client.getRegistryClient().groups().byGroupId(resolvedGroupId).rules().byRuleType(ruleType).get());
            printRule(output, rule, outputType);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error retrieving rule '")
                        .append(ruleType)
                        .append("' for group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
