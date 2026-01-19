package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import picocli.CommandLine.Command;

import java.util.Objects;

import static io.apicurio.registry.cli.utils.Utils.isBlank;

@Command(
        name = "context",
        aliases = {"contexts", "ctx"},
        description = "Work with contexts",
        subcommands = {
                ContextCreateCommand.class,
                ContextDeleteCommand.class,
        }
)
public class ContextCommand extends AbstractCommand {

    @Override
    public void run(OutputBuffer output) throws Exception {
        output.writeStdOutChunk(out -> {
            var currenContext = Config.getInstance().read().getCurrentContext();
            if (isBlank(currenContext)) {
                out.append("No current context is set.");
            } else {
                out.append("Current context is '").append(currenContext).append("'.");
            }
            out.append('\n');
            var table = new TableBuilder();
            table.addColumns("ID", "Registry URL");
            Config.getInstance().read().getContext().forEach((id, context) -> {
                table.addRow(id + (Objects.equals(id, currenContext) ? "*" : ""), context.getRegistryUrl());
            });
            table.print(out);
        });
    }
}
