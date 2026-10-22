package io.apicurio.registry.cli.context;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.utils.Utils.isBlank;

/** Updates an existing context's registry URL, group ID, or artifact ID. */
@Command(
        name = "update",
        description = "Update an existing context"
)
public class ContextUpdateCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "Context name. If not provided, updates the current context."
    )
    private String name;

    @Option(
            names = {"-r", "--registry-url"},
            description = "Updated registry URL."
    )
    private String registryUrl;

    @Option(
            names = {"-g", "--group"},
            description = "Updated group ID."
    )
    private String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Updated artifact ID."
    )
    private String artifactId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (registryUrl == null && groupId == null && artifactId == null) {
            throw new CliException("At least one update option is required (--registry-url, --group, or --artifact).",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var configModel = config.read();
        final var contextName = isBlank(name) ? configModel.getCurrentContext() : name;
        if (isBlank(contextName)) {
            throw new CliException("No context specified and no current context is set.",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var context = configModel.getContext().get(contextName);
        if (context == null) {
            throw new CliException("Context '" + contextName + "' does not exist.",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        if (registryUrl != null) {
            if (isBlank(registryUrl)) {
                throw new CliException("Registry URL cannot be empty.",
                        CliException.VALIDATION_ERROR_RETURN_CODE);
            }
            context.setRegistryUrl(registryUrl);
        }
        if (groupId != null) {
            context.setGroupId(groupId);
        }
        if (artifactId != null) {
            context.setArtifactId(artifactId);
        }
        config.write(configModel);
        output.writeStdOutChunk(out -> {
            out.append("Context '").append(contextName).append("' updated successfully.\n");
        });
    }
}
