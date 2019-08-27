package io.apicurio.registry.rules;

import java.util.Objects;

import io.apicurio.registry.types.ArtifactType;

/**
 * @author Ales Justin
 */
public class RuleContext {
    private final String artifactId;
    private final ArtifactType artifactType;
    private final String configuration;
    private final String currentContent;
    private final String updatedContent;

    /**
     * Constructor.
     * @param artifactId
     * @param artifactType
     * @param configuration
     * @param currentContent
     * @param updatedContent
     */
    public RuleContext(String artifactId, ArtifactType artifactType, String configuration,
            String currentContent, String updatedContent) {
        this.artifactId = Objects.requireNonNull(artifactId);
        this.artifactType = Objects.requireNonNull(artifactType);
        this.configuration = Objects.requireNonNull(configuration);
        this.currentContent = Objects.requireNonNull(currentContent);
        this.updatedContent = Objects.requireNonNull(updatedContent);
    }
    
    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the artifactType
     */
    public ArtifactType getArtifactType() {
        return artifactType;
    }

    /**
     * @return the configuration
     */
    public String getConfiguration() {
        return configuration;
    }

    /**
     * @return the currentContent
     */
    public String getCurrentContent() {
        return currentContent;
    }
    
    /**
     * @return the updatedContent
     */
    public String getUpdatedContent() {
        return updatedContent;
    }
}
