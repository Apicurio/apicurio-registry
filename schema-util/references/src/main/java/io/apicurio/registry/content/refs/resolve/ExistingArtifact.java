package io.apicurio.registry.content.refs.resolve;

/**
 * A reference target that already exists in the registry and will not be registered.
 *
 * @param referenceName the name used by the referencing schema
 * @param groupId the group id
 * @param artifactId the artifact id
 * @param version the version
 */
public record ExistingArtifact(String referenceName, String groupId, String artifactId, String version)
        implements ReferenceTarget {
}
