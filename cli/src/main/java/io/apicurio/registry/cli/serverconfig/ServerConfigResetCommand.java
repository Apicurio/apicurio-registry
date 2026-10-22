package io.apicurio.registry.cli.serverconfig;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(
        name = "reset",
        description = "Reset a server configuration property to its default value"
)
public class ServerConfigResetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The property name."
    )
    private String propertyName;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        client.getRegistryClient().admin().config().properties()
                .byPropertyName(propertyName).delete();

        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out));
            case table -> output.writeStdOutChunk(out -> successMessage(out));
        }

        final var prop = client.getRegistryClient().admin().config().properties()
                .byPropertyName(propertyName).get();
        ServerConfigUtil.printProperty(output, prop, outputType);
    }

    private void successMessage(final StringBuilder out) {
        out.append("Property '").append(propertyName).append("' reset to default.\n");
    }
}
