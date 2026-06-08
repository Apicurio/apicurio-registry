package io.apicurio.registry.cli.rolemapping;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete a role mapping"
)
public class RoleMappingDeleteCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The principal ID."
    )
    private String principalId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        client.getRegistryClient().admin().roleMappings()
                .byPrincipalId(principalId).delete();

        output.writeStdOutChunk(out -> {
            out.append("Role mapping for '").append(principalId).append("' deleted successfully.\n");
        });
    }
}
