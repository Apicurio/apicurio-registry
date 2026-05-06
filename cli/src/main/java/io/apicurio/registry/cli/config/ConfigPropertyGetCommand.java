package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

@Command(
        name = "get",
        description = "Get a configuration property value"
)
public class ConfigPropertyGetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "Property name"
    )
    String key;

    @Override
    public void run(OutputBuffer output) throws Exception {
        var value = config.read().getConfig().get(key);
        if (value == null) {
            throw new CliException("Property '" + key + "' is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        output.writeStdOutLine(value);
    }
}
