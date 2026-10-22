package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.VersionState;

public class InvalidVersionStateException extends RegistryStorageException {

    private static final long serialVersionUID = 1L;

    public InvalidVersionStateException(String groupId, String artifactId, String version,
            VersionState state) {
        super(String.format("Artifact %s [%s] in group (%s) is not active: %s", artifactId, version, groupId,
                state));
    }

    public InvalidVersionStateException(VersionState previousState, VersionState newState) {
        super(errorMsg(previousState, newState));
    }

    public static String errorMsg(VersionState previousState, VersionState newState) {
        return String.format("Cannot transition artifact from %s to %s", previousState, newState);
    }

}
