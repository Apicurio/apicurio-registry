package io.apicurio.registry.cli.content;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.RegistryClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "content",
        description = "Retrieve artifact content by global ID, content ID, or SHA-256 hash"
)
public class ContentCommand extends AbstractCommand {

    @ArgGroup(exclusive = true, multiplicity = "1")
    private ContentIdentifier identifier;

    static class ContentIdentifier {
        @Option(
                names = {"--global-id"},
                description = "Retrieve content by global ID."
        )
        Long globalId;

        @Option(
                names = {"--content-id"},
                description = "Retrieve content by content ID."
        )
        Long contentId;

        @Option(
                names = {"--hash"},
                description = "Retrieve content by SHA-256 hash."
        )
        String contentHash;
    }

    @Option(
            names = {"-o", "--output-type"},
            description = "Output format. Valid values: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.",
            defaultValue = "raw"
    )
    private ContentOutputType outputType;

    enum ContentOutputType {
        raw,
        json
    }

    @Override
    public void run(final OutputBuffer output) throws Exception {
        validateIdentifier();

        try (final InputStream content = fetchContent(client.getRegistryClient())) {
            final var text = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            output.writeStdOutChunkWithException(out -> {
                switch (outputType) {
                    case raw -> {
                        out.append(text);
                        if (!text.endsWith("\n")) {
                            out.append('\n');
                        }
                    }
                    case json -> {
                        out.append(MAPPER.writeValueAsString(buildJsonResult(text)));
                        out.append('\n');
                    }
                }
            });
        } catch (IOException ex) {
            throw new CliException("Could not read content.", ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private void validateIdentifier() {
        if (identifier.globalId != null && identifier.globalId <= 0) {
            throw new CliException("--global-id must be a positive number.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
        if (identifier.contentId != null && identifier.contentId <= 0) {
            throw new CliException("--content-id must be a positive number.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
        if (identifier.contentHash != null && identifier.contentHash.isBlank()) {
            throw new CliException("--hash must not be blank.",
                    VALIDATION_ERROR_RETURN_CODE);
        }
    }

    private Map<String, Object> buildJsonResult(final String text) {
        final Map<String, Object> result = new LinkedHashMap<>();
        if (identifier.globalId != null) {
            result.put("globalId", identifier.globalId);
        } else if (identifier.contentId != null) {
            result.put("contentId", identifier.contentId);
        } else {
            result.put("hash", identifier.contentHash);
        }
        result.put("content", text);
        return result;
    }

    private InputStream fetchContent(final RegistryClient registryClient) {
        if (identifier.globalId != null) {
            return registryClient.ids().globalIds().byGlobalId(identifier.globalId).get();
        } else if (identifier.contentId != null) {
            return registryClient.ids().contentIds().byContentId(identifier.contentId).get();
        } else {
            return registryClient.ids().contentHashes().byContentHash(identifier.contentHash).get();
        }
    }
}
