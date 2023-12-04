package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.ArtifactState;


public class InvalidArtifactStateException extends RegistryStorageException {

    private static final long serialVersionUID = 1L;

    public InvalidArtifactStateException(String groupId, String artifactId, String version, ArtifactState state) {
        super(String.format("Artifact %s [%s] in group (%s) is not active: %s", artifactId, version, groupId, state));
    }

    public InvalidArtifactStateException(ArtifactState previousState, ArtifactState newState) {
        super(errorMsg(previousState, newState));
    }

    public static String errorMsg(ArtifactState previousState, ArtifactState newState) {
        return String.format("Cannot transition artifact from %s to %s", previousState, newState);
    }

}
