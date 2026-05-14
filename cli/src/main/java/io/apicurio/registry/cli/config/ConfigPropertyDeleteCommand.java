package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete one or more configuration properties"
)
public class ConfigPropertyDeleteCommand extends AbstractCommand {

    @Parameters(
            arity = "1..*",
            description = "Property names to delete"
    )
    List<String> keys;

    @Override
    public void run(OutputBuffer output) throws Exception {
        var configModel = config.read();
        for (var key : keys) {
            if (!configModel.getConfig().containsKey(key)) {
                throw new CliException("Property '" + key + "' is not set.", VALIDATION_ERROR_RETURN_CODE);
            }
        }
        keys.forEach(configModel.getConfig()::remove);
        config.write(configModel);
        output.writeStdOutLine(keys.size() == 1 ? "Property deleted." : keys.size() + " properties deleted.");
    }
}
