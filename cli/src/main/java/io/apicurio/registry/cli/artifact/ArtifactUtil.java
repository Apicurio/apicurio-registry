package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.rest.client.RegistryClient;

import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Shared utility for resolving the group ID across artifact commands.
 * Priority: CLI flag → context groupId → "default".
 */
public final class ArtifactUtil {

    private static final String DEFAULT_GROUP = "default";

    private ArtifactUtil() {
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
}
