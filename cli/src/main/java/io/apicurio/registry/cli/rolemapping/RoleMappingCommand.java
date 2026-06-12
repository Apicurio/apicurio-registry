package io.apicurio.registry.cli.rolemapping;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.RoleMapping;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import static io.apicurio.registry.cli.rolemapping.RoleMappingUtil.printRoleMappingJson;
import static io.apicurio.registry.cli.utils.Columns.PRINCIPAL_ID;
import static io.apicurio.registry.cli.utils.Columns.PRINCIPAL_NAME;
import static io.apicurio.registry.cli.utils.Columns.ROLE;

@Command(
        name = "role",
        aliases = {"roles"},
        description = "Work with role mappings",
        subcommands = {
                RoleMappingCreateCommand.class,
                RoleMappingGetCommand.class,
                RoleMappingUpdateCommand.class,
                RoleMappingDeleteCommand.class
        }
)
public class RoleMappingCommand extends AbstractCommand {

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var results = client.getRegistryClient().admin().roleMappings().get();
        final var mappings = Optional.ofNullable(results.getRoleMappings()).orElse(List.of());

        switch (outputType.getOutputType()) {
            case json -> printRoleMappingJson(output, mappings);
            case table -> {
                if (mappings.isEmpty()) {
                    output.writeStdOutChunk(out -> out.append("No role mappings found.\n"));
                } else {
                    output.writeStdOutChunk(out -> {
                        final var table = new TableBuilder();
                        table.addColumns(PRINCIPAL_ID, ROLE, PRINCIPAL_NAME);
                        for (final RoleMapping mapping : mappings) {
                            table.addRow(
                                    mapping.getPrincipalId(),
                                    mapping.getRole() != null ? mapping.getRole().getValue() : "",
                                    mapping.getPrincipalName()
                            );
                        }
                        table.print(out);
                    });
                }
            }
        }
    }
}
