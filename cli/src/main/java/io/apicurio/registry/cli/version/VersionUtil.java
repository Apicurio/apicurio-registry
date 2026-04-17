package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Shared utility for resolving the artifactId across version commands.
 * Priority: CLI flag → context artifactId. No default — must be provided.
 */
final class VersionUtil {

    private VersionUtil() {
    }

    static String resolveArtifactId(final String artifactId, final Config config) {
        if (!isBlank(artifactId)) {
            return artifactId;
        }
        final var configModel = config.read();
        final var contextName = configModel.getCurrentContext();
        if (!isBlank(contextName)) {
            final var context = configModel.getContext().get(contextName);
            if (context != null && !isBlank(context.getArtifactId())) {
                return context.getArtifactId();
            }
        }
        throw new CliException("Artifact ID is required. Provide it via --artifact or set it in the context.",
                VALIDATION_ERROR_RETURN_CODE);
    }
}
