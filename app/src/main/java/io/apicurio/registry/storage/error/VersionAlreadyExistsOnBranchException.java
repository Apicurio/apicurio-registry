package io.apicurio.registry.storage.error;

import lombok.Getter;

@Getter
public class VersionAlreadyExistsOnBranchException extends AlreadyExistsException {

    private static final long serialVersionUID = 3567623491368394677L;

    private String groupId;
    private String artifactId;
    private String version;
    private String branchId;

    public VersionAlreadyExistsOnBranchException(String groupId, String artifactId, String version,
            String branchId) {
        super(message(groupId, artifactId, version, branchId));
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.branchId = branchId;
    }

    private static String message(String groupId, String artifactId, String version, String branchId) {
        return "Version '" + version + "' (for artifact ID '" + artifactId + "' " + "in group '" + groupId
                + "') already exists in branch '" + branchId + "'.";
    }
}
