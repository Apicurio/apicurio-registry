package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.rest.client.RegistryClient;

import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Shared utility for resolving the group ID across artifact commands.
 * Priority: CLI flag → context groupId → "default".
 */
final class ArtifactUtil {

    private static final String DEFAULT_GROUP = "default";

    private ArtifactUtil() {
    }

    static String resolveGroupId(final String groupId) {
        if (!isBlank(groupId)) {
            return groupId;
        }
        final var config = Config.getInstance().read();
        final var contextName = config.getCurrentContext();
        if (!isBlank(contextName)) {
            final var context = config.getContext().get(contextName);
            if (context != null && !isBlank(context.getGroupId())) {
                return context.getGroupId();
            }
        }
        return DEFAULT_GROUP;
    }

    // Validates the group exists. Skips validation for the "default" group as it is implicit.
    static void validateGroup(final RegistryClient client, final String groupId) {
        if (!DEFAULT_GROUP.equals(groupId)) {
            client.groups().byGroupId(groupId).get();
        }
    }
}
