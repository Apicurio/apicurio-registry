package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl.ArtifactReferenceBuilder;

/**
 * This class holds the information that reference one Artifact in Apicurio Registry. It will always make
 * reference to an artifact in a group. Optionally it can reference to a specific version.
 */
public interface ArtifactReference {

    boolean hasValue();

    /**
     * @return the groupId
     */
    String getGroupId();

    /**
     * @return the artifactId
     */
    String getArtifactId();

    /**
     * @return the version
     */
    String getVersion();

    /**
     * @return the globalId
     */
    Long getGlobalId();

    /**
     * @return the contentId
     */
    Long getContentId();

    /**
     * @return the contentHash
     */
    String getContentHash();

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    int hashCode();

    /**
     * Logical equality. Two artifact references are equal, if they MUST refer to the same artifact.
     */
    @Override
    boolean equals(Object obj);

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    String toString();

    public static ArtifactReference fromGlobalId(Long globalId) {
        return builder().globalId(globalId).build();
    }

    public static ArtifactReferenceBuilder builder() {
        return new ArtifactReferenceBuilder();
    }

}