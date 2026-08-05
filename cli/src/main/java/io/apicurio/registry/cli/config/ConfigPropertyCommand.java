package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
        name = "config",
        aliases = {"cfg"},
        description = "Manage configuration properties",
        subcommands = {
                ConfigPropertyGetCommand.class,
                ConfigPropertySetCommand.class,
                ConfigPropertyDeleteCommand.class,
        }
)
public class ConfigPropertyCommand extends AbstractCommand {

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(OutputBuffer output) throws Exception {
        output.writeStdOutChunk(out -> {
            var table = new TableBuilder();
            table.addColumns("Property", "Value");
            config.read().getConfig().entrySet().stream()
                    .filter(e -> !ConfigProperties.isInternal(e.getKey()))
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> table.addRow(e.getKey(), e.getValue()));
            table.setSelectedColumns(columns.getColumns());
            table.print(out);
        });
    }
}
