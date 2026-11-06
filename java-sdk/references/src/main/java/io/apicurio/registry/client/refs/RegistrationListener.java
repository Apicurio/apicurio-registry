package io.apicurio.registry.client.refs;

import io.apicurio.registry.content.refs.resolve.PlannedArtifact;
import io.apicurio.registry.rest.client.models.VersionMetaData;

/**
 * Receives progress notifications from a {@link ReferenceRegistrar}.
 */
public interface RegistrationListener {

    /**
     * Called after an artifact has been registered (or dry-run registered).
     *
     * @param artifact the planned artifact
     * @param result the version metadata returned by the registry
     */
    default void onRegistered(PlannedArtifact artifact, VersionMetaData result) {
    }

    /**
     * Called for each non-fatal warning.
     *
     * @param message the warning message
     */
    default void onWarning(String message) {
    }

    /**
     * Called in a dry run for an artifact that was not sent because it depends on artifacts of the same plan
     * that were not persisted.
     *
     * @param artifact the skipped planned artifact
     * @param reason a human-readable reason naming the unregistered dependencies
     */
    default void onSkipped(PlannedArtifact artifact, String reason) {
    }
}
