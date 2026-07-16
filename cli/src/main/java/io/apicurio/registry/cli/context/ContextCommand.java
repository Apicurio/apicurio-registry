package io.apicurio.registry.cli.context;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

@Command(
        name = "context",
        aliases = {"contexts", "ctx"},
        description = "Work with contexts",
        subcommands = {
                ContextCreateCommand.class,
                ContextUpdateCommand.class,
                ContextDeleteCommand.class,
                ContextUseCommand.class,
        }
)
public class ContextCommand extends AbstractCommand {

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(OutputBuffer output) throws Exception {
        output.writeStdOutChunk(out -> {
            var currentContext = config.read().getCurrentContext();
            if (isBlank(currentContext)) {
                out.append("No current context is set.");
            } else {
                out.append("Current context is '").append(currentContext).append("'.");
            }
            out.append('\n');
            var table = new TableBuilder();
            table.addColumns("ID", "Registry URL", GROUP_ID, ARTIFACT_ID);
            config.read().getContext().forEach((id, context) -> {
                table.addRow(id + (Objects.equals(id, currentContext) ? "*" : ""), context.getRegistryUrl(), context.getGroupId(), context.getArtifactId());
            });
            table.selectColumns(columns.getColumns());
            table.print(out);
        });
    }
}
