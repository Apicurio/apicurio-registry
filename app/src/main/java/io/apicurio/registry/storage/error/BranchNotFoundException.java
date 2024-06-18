package io.apicurio.registry.storage.error;

import lombok.Getter;

@Getter
public class BranchNotFoundException extends NotFoundException {

    private final String groupId;
    private final String artifactId;
    private final String branchId;

    public BranchNotFoundException(String groupId, String artifactId, String branchId) {
        super(message(groupId, artifactId, branchId));
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.branchId = branchId;
    }

    public BranchNotFoundException(String groupId, String artifactId, String branchId, Exception cause) {
        super(message(groupId, artifactId, branchId), cause);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.branchId = branchId;
    }

    private static String message(String groupId, String artifactId, String branchId) {
        return "No branch '" + branchId + "' was found in " + groupId + "/" + artifactId + ".";
    }
}
