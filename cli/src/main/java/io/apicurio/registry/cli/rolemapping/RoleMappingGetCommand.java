package io.apicurio.registry.cli.rolemapping;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.rolemapping.RoleMappingUtil.printRoleMapping;

@Command(
        name = "get",
        description = "Get a role mapping by principal ID"
)
public class RoleMappingGetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The principal ID."
    )
    private String principalId;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var mapping = client.getRegistryClient().admin().roleMappings()
                .byPrincipalId(principalId).get();
        printRoleMapping(output, mapping, outputType);
    }
}
