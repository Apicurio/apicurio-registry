package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

@Command(
        name = "set",
        description = "Set one or more configuration properties (key=value)"
)
public class ConfigPropertySetCommand extends AbstractCommand {

    @Parameters(
            arity = "1..*",
            description = "Properties to set in key=value format"
    )
    List<String> properties;

    @Override
    public void run(OutputBuffer output) throws Exception {
        // Parse and validate every argument before applying any change so the operation is atomic:
        // a malformed argument must not leave some properties already set.
        final Map<String, String> parsed = new LinkedHashMap<>();
        for (var prop : properties) {
            var eqIndex = prop.indexOf('=');
            if (eqIndex < 1) {
                throw new CliException("Invalid property format: '" + prop + "'. Expected key=value.", VALIDATION_ERROR_RETURN_CODE);
            }
            var key = prop.substring(0, eqIndex);
            var value = prop.substring(eqIndex + 1);
            parsed.put(key, value);
        }
        parsed.forEach(config::setProperty);
        output.writeStdOutLine(properties.size() == 1 ? "Property set." : properties.size() + " properties set.");
    }
}
