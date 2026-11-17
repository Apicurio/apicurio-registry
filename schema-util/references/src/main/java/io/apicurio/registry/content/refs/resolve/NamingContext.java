package io.apicurio.registry.content.refs.resolve;

/**
 * Inputs available to an {@link ArtifactNamingStrategy} when choosing coordinates for a referenced schema.
 *
 * @param target the indexed source being registered as a reference
 * @param resource the raw reference string as it appears in the parent document
 * @param parentArtifactType the artifact type of the referencing (parent) document
 * @param parent the coordinates of the referencing (parent) artifact
 */
public record NamingContext(IndexedSource target, String resource, String parentArtifactType,
        ArtifactCoordinates parent) {
}
