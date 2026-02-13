package io.apicurio.registry.storage.impl.search;

/**
 * Determines how the Lucene search index is updated.
 */
public enum IndexUpdateMode {
    /**
     * Index is updated synchronously when versions are created/modified. Use for single-node deployments
     * (KafkaSQL, GitOps, in-memory, or configured single-node SQL).
     */
    SYNCHRONOUS,

    /**
     * Index is updated asynchronously by polling for changes. Use for multi-node deployments with shared
     * SQL storage.
     */
    ASYNCHRONOUS
}
