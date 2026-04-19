package io.apicurio.registry.cli.group;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.common.RuleUtil.printRuleTypes;
import static io.apicurio.registry.cli.common.RuleUtil.rejectDefaultGroup;

@Command(
        name = "rule",
        aliases = {"rules"},
        description = "Work with group rules",
        subcommands = {
                GroupRuleCreateCommand.class,
                GroupRuleDeleteCommand.class,
                GroupRuleGetCommand.class,
                GroupRuleUpdateCommand.class
        }
)
public class GroupRuleCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context. Group rules are not available for the 'default' group."
    )
    private String groupId;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        rejectDefaultGroup(resolvedGroupId);
        try {
            final var ruleTypes = client.getRegistryClient().groups().byGroupId(resolvedGroupId).rules().get();
            printRuleTypes(ruleTypes, output, outputType);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error listing rules for group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
