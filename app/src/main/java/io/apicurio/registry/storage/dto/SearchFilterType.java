package io.apicurio.registry.storage.dto;

/**
 * Defines the types of filter criteria that can be used when searching for artifacts or versions in the
 * registry. Each value corresponds to a field or property that can be matched against.
 */
public enum SearchFilterType {

    groupId, artifactId, version, name, description, labels, contentHash, canonicalHash, globalId, contentId, state, artifactType

}
