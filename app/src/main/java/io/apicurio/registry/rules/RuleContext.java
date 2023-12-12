package io.apicurio.registry.rules;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains all of the information needed by a rule executor, including the rule-specific configuration,
 * current and updated content, and any other meta-data needed.
 */
public class RuleContext {
    private final String groupId;
    private final String artifactId;
    private final String artifactType;
    private final String configuration;
    private final List<ContentHandle> currentContent;
    private final ContentHandle updatedContent;
    private final List<ArtifactReference> references;
    private final Map<String, ContentHandle> resolvedReferences;

    /**
     * Constructor.
     *
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param configuration
     * @param currentContent
     * @param updatedContent
     */
    public RuleContext(String groupId, String artifactId, String artifactType, String configuration,
            List<ContentHandle> currentContent, ContentHandle updatedContent,
            List<ArtifactReference> references, Map<String, ContentHandle> resolvedReferences) {
        this.groupId = groupId;
        this.artifactId = Objects.requireNonNull(artifactId);
        this.artifactType = Objects.requireNonNull(artifactType);
        this.configuration = Objects.requireNonNull(configuration);
        this.currentContent = currentContent; // Current Content will be null when creating an artifact.
        this.updatedContent = Objects.requireNonNull(updatedContent);
        this.references = Objects.requireNonNull(references);
        this.resolvedReferences = Objects.requireNonNull(resolvedReferences);
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
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
    public String getArtifactType() {
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
    public List<ContentHandle> getCurrentContent() {
        return currentContent;
    }

    /**
     * @return the updatedContent
     */
    public ContentHandle getUpdatedContent() {
        return updatedContent;
    }

    /**
     * @return the references
     */
    public Map<String, ContentHandle> getResolvedReferences() {
        return resolvedReferences;
    }

    /**
     * @return the references
     */
    public List<ArtifactReference> getReferences() {
        return references;
    }
}
