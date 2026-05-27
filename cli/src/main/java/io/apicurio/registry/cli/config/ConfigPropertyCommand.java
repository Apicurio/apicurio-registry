package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import picocli.CommandLine.Command;

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

    static final String INTERNAL_PREFIX = "internal.";

    @Override
    public void run(OutputBuffer output) throws Exception {
        output.writeStdOutChunk(out -> {
            var table = new TableBuilder();
            table.addColumns("Property", "Value");
            config.read().getConfig().entrySet().stream()
                    .filter(e -> !e.getKey().startsWith(INTERNAL_PREFIX))
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> table.addRow(e.getKey(), e.getValue()));
            table.print(out);
        });
    }
}
