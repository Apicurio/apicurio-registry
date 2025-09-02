package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new context"
)
public class ContextCreateCommand extends AbstractCommand {

    @Parameters(
            index = "0"
    )
    String name;

    @Parameters(
            index = "1"
    )
    String registryUrl;

    @Option(
            names = {"--no-switch-current"},
            description = "Do not make the newly added context the current context.",
            defaultValue = "false"
    )
    private boolean noSwitchCurrent;

    @Override
    public void run(OutputBuffer output) throws Exception {
        var config = Config.getInstance().read();
        if (config.getContext().get(name) != null) {
            throw new CliException("Context '" + name + "' already exists.", CliException.VALIDATION_ERROR_RETURN_CODE);
        } else {
            config.getContext().put(name, ConfigModel.Context.builder()
                    .registryUrl(registryUrl)
                    .build());
            output.writeStdOutChunk(out -> {
                if (!noSwitchCurrent) {
                    config.setCurrentContext(name);
                    out.append("Current context '").append(name).append("' added.");
                } else {
                    out.append("Context '").append(name).append("' added.");
                }
                out.append('\n');
                Config.getInstance().write(config);
            });
        }
    }
}
