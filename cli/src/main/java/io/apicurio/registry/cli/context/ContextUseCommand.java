package io.apicurio.registry.cli.context;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "use",
        aliases = {"set", "switch"},
        description = "Switch to a different context"
)
public class ContextUseCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The context name to switch to."
    )
    private String name;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (config.getContext(name) == null) {
            throw new CliException("Context '" + name + "' does not exist.",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        if (name.equals(config.getCurrentContext())) {
            output.writeStdOutChunk(out -> {
                out.append("Context '").append(name).append("' is already the current context.\n");
            });
            return;
        }
        final var previousContext = config.getCurrentContext();
        config.setCurrentContext(name);
        output.writeStdOutChunk(out -> {
            out.append("Switched to context '").append(name).append("'");
            if (previousContext != null) {
                out.append(" from '").append(previousContext).append("'");
            }
            out.append(".\n");
        });
    }
}
