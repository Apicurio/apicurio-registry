package io.apicurio.registry.storage.dto;

public enum OrderBy {
    name, createdOn, modifiedOn, // Shared
    groupId, // Group specific
    artifactId, artifactType, // Artifact specific
    globalId, version // Version specific
}
