package io.apicurio.registry.content.refs.resolve;

/**
 * Describes a root schema to register, along with optional registration overrides.
 *
 * @param location the location of the source in the {@link SchemaSourceSet}
 * @param groupId the group id
 * @param artifactId the artifact id
 * @param artifactType the artifact type, or {@code null} to detect it
 * @param contentType the content type, or {@code null} to use the source's
 * @param version the version, or {@code null} to let the registry assign one
 * @param draft whether to create a draft version, or {@code null} for the default
 */
public record RootArtifact(String location, String groupId, String artifactId, String artifactType,
        String contentType, String version, Boolean draft) {

    /**
     * Creates a root artifact with only coordinates; all other fields are {@code null}.
     *
     * @param location the source location
     * @param groupId the group id
     * @param artifactId the artifact id
     * @return the root artifact
     */
    public static RootArtifact of(String location, String groupId, String artifactId) {
        return new RootArtifact(location, groupId, artifactId, null, null, null, null);
    }
}
