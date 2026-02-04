package io.apicurio.registry.storage.error;

/**
 * Exception thrown when attempting to delete a group that still contains artifacts.
 */
public class GroupNotEmptyException extends RegistryStorageException {

    private static final long serialVersionUID = -8893847573496891L;

    private final String groupId;
    private final long artifactCount;

    public GroupNotEmptyException(String groupId, long artifactCount) {
        super("Group '" + groupId + "' is not empty (contains " + artifactCount + " artifacts)");
        this.groupId = groupId;
        this.artifactCount = artifactCount;
    }

    public String getGroupId() {
        return groupId;
    }

    public long getArtifactCount() {
        return artifactCount;
    }
}
