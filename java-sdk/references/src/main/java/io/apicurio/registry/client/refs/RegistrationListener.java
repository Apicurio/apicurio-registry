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
}
