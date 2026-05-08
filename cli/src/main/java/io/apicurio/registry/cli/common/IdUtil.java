package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.rest.client.RegistryClient;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Shared utility for resolving and validating group and artifact IDs, and validating version existence across CLI commands.
 */
public final class IdUtil {

    private static final String DEFAULT_GROUP = "default";

    private IdUtil() {
    }

    public static String resolveGroupId(final String groupId, final Config config) {
        if (!isBlank(groupId)) {
            return groupId;
        }
        final var configModel = config.read();
        final var contextName = configModel.getCurrentContext();
        if (!isBlank(contextName)) {
            final var context = configModel.getContext().get(contextName);
            if (context != null && !isBlank(context.getGroupId())) {
                return context.getGroupId();
            }
        }
        return DEFAULT_GROUP;
    }

    public static String resolveArtifactId(final String artifactId, final Config config) {
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

    // Validates the group exists. Skips validation for the "default" group as it is implicit.
    public static void validateGroup(final RegistryClient client, final String groupId) {
        if (!DEFAULT_GROUP.equals(groupId)) {
            client.groups().byGroupId(groupId).get();
        }
    }

    // Validates the artifact exists within the given group.
    public static void validateArtifact(final RegistryClient client, final String groupId, final String artifactId) {
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
    }

    // Validates the version exists for the given artifact.
    public static void validateVersion(final RegistryClient client, final String groupId,
                                       final String artifactId, final String versionExpression) {
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression).get();
    }
}
