package io.apicurio.registry.cli.group;

import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;


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
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        rejectDefaultGroup(resolvedGroupId);
        final var ruleTypes = client.getRegistryClient().groups().byGroupId(resolvedGroupId).rules().get();
        printRuleTypes(ruleTypes, output, outputType);
    }
}
