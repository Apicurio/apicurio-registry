package io.apicurio.registry.storage.dto;

/**
 * The metadata of a version together with the metadata of its artifact, read together.
 *
 * @param version the version's metadata
 * @param artifact the metadata of the version's artifact
 */
public record VersionWithArtifactMetaDataDto(ArtifactVersionMetaDataDto version, ArtifactMetaDataDto artifact) {
}
