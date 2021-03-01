package io.apicurio.registry.storage;

public class VersionContentAlreadyExists extends AlreadyExistsException {

    private final String artifactId;
    private final String content;

    /**
     * Constructor.
     */
    public VersionContentAlreadyExists(String artifactId, String content) {
        this.artifactId = artifactId;
        this.content = content;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the content
     */
    public String getContent() {
        return content;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "A Schema with ID '" + this.artifactId + "' and content '" + this.content + "' already exists.";
    }
}
