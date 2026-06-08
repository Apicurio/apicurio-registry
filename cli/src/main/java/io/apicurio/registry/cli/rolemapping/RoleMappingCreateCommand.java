package io.apicurio.registry.cli.rolemapping;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.RoleMapping;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.rolemapping.RoleMappingUtil.parseRoleType;
import static io.apicurio.registry.cli.rolemapping.RoleMappingUtil.printRoleMapping;

@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new role mapping"
)
public class RoleMappingCreateCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The principal ID."
    )
    private String principalId;

    @Parameters(
            index = "1",
            description = "The role (ADMIN, DEVELOPER, or READ_ONLY)."
    )
    private String role;

    @Option(
            names = {"--name"},
            description = "Display name for the principal."
    )
    private String principalName;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var roleType = parseRoleType(role);

        final var mapping = new RoleMapping();
        mapping.setPrincipalId(principalId);
        mapping.setRole(roleType);
        mapping.setPrincipalName(principalName);

        client.getRegistryClient().admin().roleMappings().post(mapping);

        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, principalId));
            case table -> output.writeStdOutChunk(out -> successMessage(out, principalId));
        }

        final var created = client.getRegistryClient().admin().roleMappings()
                .byPrincipalId(principalId).get();
        printRoleMapping(output, created, outputType);
    }

    private static void successMessage(final StringBuilder out, final String principalId) {
        out.append("Role mapping for '").append(principalId).append("' created successfully.\n");
    }
}
