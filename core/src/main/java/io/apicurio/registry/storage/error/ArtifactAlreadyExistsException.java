package io.apicurio.registry.storage.error;

import lombok.Getter;

public class ArtifactAlreadyExistsException extends AlreadyExistsException {

    private static final long serialVersionUID = -1015140450163088675L;

    @Getter
    private String groupId;

    @Getter
    private String artifactId;

    public ArtifactAlreadyExistsException(String groupId, String artifactId) {
        super(message(groupId, artifactId));
        this.artifactId = artifactId;
        this.groupId = groupId;
    }

    private static String message(String groupId, String artifactId) {
        return "An artifact with ID '" + artifactId + "' in group '" + groupId + "' already exists.";
    }
}
