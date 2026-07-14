package io.apicurio.registry.cli.serverconfig;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(
        name = "get",
        description = "Get a server configuration property"
)
public class ServerConfigGetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The property name."
    )
    private String propertyName;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var prop = client.getRegistryClient().admin().config().properties()
                .byPropertyName(propertyName).get();
        ServerConfigUtil.printProperty(output, prop, outputType);
    }
}
