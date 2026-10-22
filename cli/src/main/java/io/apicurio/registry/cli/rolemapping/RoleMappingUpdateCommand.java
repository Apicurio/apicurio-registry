package io.apicurio.registry.cli.rolemapping;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.UpdateRole;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.rolemapping.RoleMappingUtil.parseRoleType;
import static io.apicurio.registry.cli.rolemapping.RoleMappingUtil.printRoleMapping;

@Command(
        name = "update",
        description = "Update the role of an existing role mapping"
)
public class RoleMappingUpdateCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The principal ID."
    )
    private String principalId;

    @Option(
            names = {"--role"},
            description = "The new role (ADMIN, DEVELOPER, or READ_ONLY).",
            required = true
    )
    private String role;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var roleType = parseRoleType(role);

        final var updateRole = new UpdateRole();
        updateRole.setRole(roleType);

        client.getRegistryClient().admin().roleMappings()
                .byPrincipalId(principalId).put(updateRole);

        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, principalId));
            case table -> output.writeStdOutChunk(out -> successMessage(out, principalId));
        }

        final var updated = client.getRegistryClient().admin().roleMappings()
                .byPrincipalId(principalId).get();
        printRoleMapping(output, updated, outputType);
    }

    private static void successMessage(final StringBuilder out, final String principalId) {
        out.append("Role mapping for '").append(principalId).append("' updated successfully.\n");
    }
}
