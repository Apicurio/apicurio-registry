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
        int removed = 0;
        for (var key : keys) {
            if (configModel.getConfig().remove(key) != null) {
                removed++;
            } else {
                throw new CliException("Property '" + key + "' is not set.", VALIDATION_ERROR_RETURN_CODE);
            }
        }
        config.write(configModel);
        output.writeStdOutLine(removed == 1 ? "Property deleted." : removed + " properties deleted.");
    }
}
