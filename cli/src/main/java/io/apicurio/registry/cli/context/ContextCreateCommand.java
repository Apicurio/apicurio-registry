package io.apicurio.registry.cli.context;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
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
            names = {"-g", "--group"},
            description = "Group ID to use when not specified in a command."
    )
    String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Artifact ID to use when not specified in a command."
    )
    String artifactId;

    @Option(
            names = {"--no-switch-current"},
            description = "Do not make the newly added context the current context.",
            defaultValue = "false"
    )
    private boolean noSwitchCurrent;

    @Override
    public void run(OutputBuffer output) throws Exception {
        var configModel = config.read();
        if (configModel.getContext().get(name) != null) {
            throw new CliException("Context '" + name + "' already exists.", CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        configModel.getContext().put(name, ConfigModel.Context.builder()
                .registryUrl(registryUrl)
                .groupId(groupId)
                .artifactId(artifactId)
                .build());
        final boolean switchCurrent = !noSwitchCurrent;
        if (switchCurrent) {
            configModel.setCurrentContext(name);
        }
        // Persist before reporting success: if the write fails (for example a global install whose
        // shared config file is not writable by this user), the command must not print a success
        // line first. See Config#write and Config#ensureWritable.
        config.write(configModel);
        output.writeStdOutLine(switchCurrent
                ? "Current context '" + name + "' added."
                : "Context '" + name + "' added.");
    }
}
