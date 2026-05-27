package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.RuleUtil.printRuleTypes;

@Command(
        name = "rule",
        aliases = {"rules"},
        description = "Work with artifact rules",
        subcommands = {
                ArtifactRuleCreateCommand.class,
                ArtifactRuleDeleteCommand.class,
                ArtifactRuleGetCommand.class,
                ArtifactRuleUpdateCommand.class
        }
)
public class ArtifactRuleCommand extends AbstractCommand {

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

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        final var registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        final var ruleTypes = registryClient.groups().byGroupId(resolvedGroupId)
                .artifacts().byArtifactId(resolvedArtifactId).rules().get();
        printRuleTypes(ruleTypes, output, outputType);
    }
}
