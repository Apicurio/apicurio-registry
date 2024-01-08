package io.apicurio.registry.storage.error;

import io.apicurio.registry.model.GroupId;
import lombok.Getter;

public class ArtifactNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -3614783501078800654L;

    @Getter
    private String groupId;

    @Getter
    private String artifactId;


    public ArtifactNotFoundException(String groupId, String artifactId) {
        super(message(groupId, artifactId));
        this.groupId = groupId;
        this.artifactId = artifactId;
    }


    public ArtifactNotFoundException(String groupId, String artifactId, Throwable cause) {
        super(message(groupId, artifactId), cause);
        this.groupId = groupId;
        this.artifactId = artifactId;
    }


    public ArtifactNotFoundException(String artifactId) {
        super(message(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), artifactId));
        this.artifactId = artifactId;
    }


    private static String message(String groupId, String artifactId) {
        return "No artifact with ID '" + artifactId + "' in group '" + groupId + "' was found.";
    }
}
