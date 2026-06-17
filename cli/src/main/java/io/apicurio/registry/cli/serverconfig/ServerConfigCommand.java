package io.apicurio.registry.cli.serverconfig;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ConfigurationProperty;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import static io.apicurio.registry.cli.utils.Columns.LABEL;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "server-config",
        aliases = {"server-configs"},
        description = "Manage server-side configuration properties",
        subcommands = {
                ServerConfigGetCommand.class,
                ServerConfigSetCommand.class,
                ServerConfigResetCommand.class
        }
)
public class ServerConfigCommand extends AbstractCommand {

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var properties = Optional.ofNullable(
                client.getRegistryClient().admin().config().properties().get()
        ).orElse(List.of());

        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(properties));
                    out.append('\n');
                }
                case table -> {
                    if (properties.isEmpty()) {
                        out.append("No configuration properties found.\n");
                    } else {
                        final var table = new TableBuilder();
                        table.addColumns(NAME, VALUE, LABEL);
                        for (final ConfigurationProperty prop : properties) {
                            table.addRow(prop.getName(), prop.getValue(), prop.getLabel());
                        }
                        table.print(out);
                    }
                }
            }
        });
    }

}
