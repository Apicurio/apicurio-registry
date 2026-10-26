package io.apicurio.registry.auth;

/**
 * Describes how the parameters of an {@link Authorized} method identify the resource being
 * accessed. Owner-based and per-resource (grants) authorization both resolve the resource through
 * {@link AbstractAccessController#resolveResource}, so every style must document its parameter
 * contract here.
 */
public enum AuthorizedStyle {

    /** Param 0 = groupId, param 1 = artifactId. */
    GroupAndArtifact,

    /** Param 0 = groupId. */
    GroupOnly,

    /**
     * Param 0 = artifactId (Confluent compatibility API subject). The group comes from the
     * {@code X-Registry-GroupId} header, or from the subject itself when ccompat group
     * concatenation is enabled.
     */
    ArtifactOnly,

    /** Param 0 = global ID ({@code long} or any {@link Number}). */
    GlobalId,

    /** Param 0 = {@code "groupId/artifactId"}, split at the first slash. */
    QualifiedArtifactName,

    /** Param 1 = URL-encoded Iceberg namespace (param 0 is the catalog prefix). */
    IcebergNamespace,

    /** Param 1 = URL-encoded Iceberg namespace, param 2 = table or view name. */
    IcebergTable,

    /**
     * Param 0 = content ID. Content can be shared by many artifacts; access is granted if the
     * caller can access at least one artifact version that uses it.
     */
    ContentId,

    /** Param 0 = content hash. Same semantics as {@link #ContentId}. */
    ContentHash,

    /**
     * Param 0 = Confluent compatibility API schema ID: a global ID when ccompat legacy ID mode is
     * enabled, otherwise a content ID.
     */
    CCompatSchemaId,

    /** The method does not address a single resource. */
    None

}
