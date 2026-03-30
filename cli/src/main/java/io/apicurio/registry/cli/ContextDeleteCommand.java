package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete one or more contexts"
)
public class ContextDeleteCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "Name of the context to delete"
    )
    String name;

    @Option(
            names = {"--all"},
            description = "Delete all contexts. NOTE: This will also remove the currently selected context.",
            defaultValue = "false"
    )
    private boolean all;

    @Override
    public void run(OutputBuffer output) throws Exception {
        var configModel = config.read();

        if (all && name != null) {
            throw new CliException("Cannot specify both a context name and --all option.", VALIDATION_ERROR_RETURN_CODE);
        }
        if (!all && name == null) {
            throw new CliException("Must specify either a context name or --all option.", VALIDATION_ERROR_RETURN_CODE);
        }

        if (!all) {
            // Delete a specific context
            if (!configModel.getContext().containsKey(name)) {
                throw new CliException("Context '" + name + "' does not exist.", VALIDATION_ERROR_RETURN_CODE);
            }
            configModel.getContext().remove(name);
            // If the deleted context was the current context, clear it
            if (name.equals(configModel.getCurrentContext())) {
                configModel.setCurrentContext(null);
            }
            config.write(configModel);
            output.writeStdOutChunk(out -> out.append("Context '").append(name).append("' deleted.\n"));
        } else {
            // Delete all contexts
            if (configModel.getContext().isEmpty()) {
                output.writeStdOutChunk(out -> out.append("No contexts to delete.\n"));
            } else {
                int count = configModel.getContext().size();
                configModel.getContext().clear();
                configModel.setCurrentContext(null);
                config.write(configModel);
                output.writeStdOutChunk(out -> out.append("Deleted all ").append(count).append(" context(s).\n"));
            }
        }
    }
}
