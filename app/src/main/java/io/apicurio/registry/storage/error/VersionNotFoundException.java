package io.apicurio.registry.storage.error;

import lombok.Getter;


public class VersionNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 969959730600115392L;

    @Getter
    private String groupId;

    @Getter
    private String artifactId;

    @Getter
    private String version;

    @Getter
    private Long globalId;


    public VersionNotFoundException(long globalId) {
        super(message(null, null, null, globalId));
        this.globalId = globalId;
    }


    public VersionNotFoundException(String groupId, String artifactId, String version) {
        super(message(groupId, artifactId, version, null));
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }


    public VersionNotFoundException(String groupId, String artifactId, String version, Throwable cause) {
        super(message(groupId, artifactId, version, null), cause);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }


    private static String message(String groupId, String artifactId, String version, Long globalId) {
        if (globalId != null) {
            return "No version with global ID '" + globalId + "' found.";
        } else {
            return "No version '" + version + "' found for artifact with ID '" + artifactId + "' " +
                    "in group '" + groupId + "'.";
        }
    }
}
