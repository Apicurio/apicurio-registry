package io.apicurio.registry.auth;

/**
 * A global ID's coordinates, exactly as {@link io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto}
 * stores them (no normalization). Deliberately holds nothing beyond this: the mutable parts of a version's
 * metadata (state, labels, name, description, owner, timestamps) must never be cached by global ID, since a
 * global ID's coordinates are the only part of that metadata that never changes once assigned.
 */
public record GlobalIdCoordinates(String groupId, String artifactId, String version) {
}
