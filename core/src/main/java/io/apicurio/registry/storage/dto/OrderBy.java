package io.apicurio.registry.storage.dto;

/**
 * Defines the fields by which search results can be sorted.
 */
public enum OrderBy {
    name, createdOn, modifiedOn, // Shared
    groupId, // Group specific
    artifactId, artifactType, // Artifact specific
    globalId, version // Version specific
}
