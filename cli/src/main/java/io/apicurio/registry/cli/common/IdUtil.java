package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.RegistryClient;
import java.util.Objects;
import java.util.function.Consumer;

import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;

import java.util.ArrayList;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Shared utility for resolving and validating group and artifact IDs, and validating version existence across CLI commands.
 */
public final class IdUtil {

    public static final String DEFAULT_GROUP = "default";

    private static final String AUTO_CONTEXT_UPDATE_PROPERTY = "context.auto-update";

    private IdUtil() {
    }

    public static boolean isDefaultGroup(final String groupId) {
        return isBlank(groupId) || DEFAULT_GROUP.equals(groupId);
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

    /**
     * Saves the given group ID to the current context, if the {@code context.auto-update} property is enabled.
     * Clears the context's artifact ID when the group actually changes, since an artifact from a
     * different group no longer applies.
     */
    public static void updateGroupContext(final String groupId, final Config config, final OutputBuffer output) {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(config);
        Objects.requireNonNull(output);
        updateContext(config, output, context -> {
            if (!groupId.equals(context.getGroupId())) {
                context.setArtifactId(null);
            }
            context.setGroupId(groupId);
        });
    }

    /**
     * Saves the given group and artifact IDs to the current context, if the {@code context.auto-update}
     * property is enabled.
     */
    public static void updateArtifactContext(final String groupId, final String artifactId, final Config config,
                                              final OutputBuffer output) {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(config);
        Objects.requireNonNull(output);
        updateContext(config, output, context -> {
            context.setGroupId(groupId);
            context.setArtifactId(artifactId);
        });
    }

    private static void updateContext(final Config config, final OutputBuffer output,
                                       final Consumer<ConfigModel.Context> mutator) {
        if (!"true".equalsIgnoreCase(config.read().getConfig().get(AUTO_CONTEXT_UPDATE_PROPERTY))) {
            return;
        }
        final var configModel = config.read();
        final var contextName = configModel.getCurrentContext();
        if (isBlank(contextName)) {
            return;
        }
        final var context = configModel.getContext().get(contextName);
        if (context == null) {
            return;
        }
        mutator.accept(context);
        try {
            config.write(configModel);
        } catch (Exception ex) {
            output.writeStdErrChunk(out -> out.append("Warning: Could not update context: ").append(ex.getMessage()).append("\n"));
        }
    }

    // Validates the group exists. Skips validation for the "default" group as it is implicit.
    public static void validateGroup(final RegistryClient client, final String groupId) {
        if (!isDefaultGroup(groupId)) {
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

    public static String displayGroupId(String groupId) {
        return isDefaultGroup(groupId) ? DEFAULT_GROUP : groupId;
    }

    public static void injectDefaultGroup(GroupSearchResults results, int pageNumber) {
        // count is the total number of matching groups across all pages (per OpenAPI schema),
        // so we always bump it by 1 for the implicit default group.
        if (results.getCount() != null) {
            results.setCount(results.getCount() + 1);
        }
        // Only inject the visual row on the first page.
        // This may produce pageSize + 1 rows on page 1, bounded by +1 since the default
        // group is a singleton.
        if (pageNumber == 1) {
            var groups = results.getGroups();
            if (groups == null) {
                groups = new ArrayList<>();
                results.setGroups(groups);
            }
            var defaultGroup = SearchedGroup.builder()
                    .groupId(DEFAULT_GROUP)
                    .build();
            // Intentionally pinned to the top: the default group is a special implicit
            // group that always exists and is not returned by the server. Pinning it at
            // index 0 ensures it is immediately visible regardless of --order/--orderby,
            // which is the desired UX for discoverability (see issue #8643).
            groups.add(0, defaultGroup);
        }
    }
}
