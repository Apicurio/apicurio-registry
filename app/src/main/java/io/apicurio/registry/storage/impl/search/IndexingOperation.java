package io.apicurio.registry.storage.impl.search;

/**
 * Sealed interface representing the different types of indexing operations that can be enqueued
 * for asynchronous processing by the {@link ElasticsearchIndexUpdater}. Each record type carries
 * a {@link Type} enum value so that callers can switch on the operation type without pattern
 * matching.
 */
public sealed interface IndexingOperation {

    /**
     * Enum identifying each concrete operation type, enabling enum-based switch dispatch
     * in Java 17.
     */
    enum Type {
        INDEX_VERSION,
        REINDEX_ARTIFACT_VERSIONS,
        DELETE_VERSION,
        DELETE_ARTIFACT,
        DELETE_GROUP,
        DELETE_ALL_DATA
    }

    /**
     * Returns the type discriminator for this operation.
     *
     * @return the operation type
     */
    Type type();

    /**
     * Re-indexes a single version. Used for both version creation and version state changes.
     */
    record IndexVersion(String groupId, String artifactId, String version,
                        long globalId) implements IndexingOperation {
        @Override
        public Type type() { return Type.INDEX_VERSION; }
    }

    /**
     * Re-indexes all versions of an artifact. Used when artifact-level metadata changes.
     */
    record ReindexArtifactVersions(String groupId,
                                   String artifactId) implements IndexingOperation {
        @Override
        public Type type() { return Type.REINDEX_ARTIFACT_VERSIONS; }
    }

    /**
     * Removes a single version from the index.
     */
    record DeleteVersion(String groupId, String artifactId, String version,
                         long globalId) implements IndexingOperation {
        @Override
        public Type type() { return Type.DELETE_VERSION; }
    }

    /**
     * Removes all versions of an artifact from the index.
     */
    record DeleteArtifact(String groupId, String artifactId) implements IndexingOperation {
        @Override
        public Type type() { return Type.DELETE_ARTIFACT; }
    }

    /**
     * Removes all versions in a group from the index.
     */
    record DeleteGroup(String groupId) implements IndexingOperation {
        @Override
        public Type type() { return Type.DELETE_GROUP; }
    }

    /**
     * Removes all data from the index.
     */
    record DeleteAllData() implements IndexingOperation {
        @Override
        public Type type() { return Type.DELETE_ALL_DATA; }
    }
}
