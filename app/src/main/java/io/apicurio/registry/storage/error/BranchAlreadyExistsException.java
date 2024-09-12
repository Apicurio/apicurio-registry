package io.apicurio.registry.storage.error;

import lombok.Getter;

@Getter
public class BranchAlreadyExistsException extends AlreadyExistsException {

    private final String groupId;
    private final String artifactId;
    private final String branchId;

    public BranchAlreadyExistsException(String groupId, String artifactId, String branchId) {
        super("Branch '" + branchId + "' already exists.");
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.branchId = branchId;
    }
}
