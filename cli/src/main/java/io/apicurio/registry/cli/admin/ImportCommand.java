package io.apicurio.registry.cli.admin;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

@Command(
        name = "import",
        description = "Import registry data from a ZIP file"
)
public class ImportCommand extends AbstractCommand {

    private static final String HEADER_PRESERVE_GLOBAL_ID = "X-Registry-Preserve-GlobalId";
    private static final String HEADER_PRESERVE_CONTENT_ID = "X-Registry-Preserve-ContentId";

    @Option(
            names = {"-f", "--file"},
            required = true,
            description = "Input file path of the ZIP archive to import."
    )
    private String file;

    @Option(
            names = {"--no-require-empty"},
            description = "Allow importing into a non-empty registry."
    )
    private boolean noRequireEmpty;

    @Option(
            names = {"--no-preserve-ids"},
            description = "Do not preserve global and content IDs during import."
    )
    private boolean noPreserveIds;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var filePath = Path.of(file);
        if (!Files.exists(filePath)) {
            throw new CliException("File not found: " + file, VALIDATION_ERROR_RETURN_CODE);
        }
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new CliException("Cannot read file: " + file, VALIDATION_ERROR_RETURN_CODE);
        }

        final var registryClient = client.getRegistryClient();

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            registryClient.admin().importEscaped().post(inputStream, r -> {
                r.queryParameters.requireEmptyRegistry = !noRequireEmpty;
                if (noPreserveIds) {
                    r.headers.add(HEADER_PRESERVE_GLOBAL_ID, "false");
                    r.headers.add(HEADER_PRESERVE_CONTENT_ID, "false");
                }
            });
        } catch (IOException ex) {
            throw new CliException("Failed to read import file: " + ex.getMessage(), ex,
                    APPLICATION_ERROR_RETURN_CODE);
        }

        output.writeStdOutChunk(out ->
                out.append("Registry data imported from '").append(file).append("'.\n"));
    }
}
