package io.apicurio.registry.storage.error;

/**
 * Exception thrown when a table commit fails due to a conflict (e.g., concurrent modification or unmet
 * requirements).
 */
public class CommitFailedException extends RegistryStorageException {

    private static final long serialVersionUID = -4892347192847312L;

    private final String groupId;
    private final String artifactId;
    private final String reason;

    public CommitFailedException(String groupId, String artifactId, String reason) {
        super("Commit failed for table '" + groupId + "/" + artifactId + "': " + reason);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.reason = reason;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getReason() {
        return reason;
    }
}
