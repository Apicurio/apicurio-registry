package io.apicurio.registry.storage;

/**
 * Enumerates the types of events emitted by the storage layer when registry data changes. These events are
 * used to notify other components (e.g. caches, Kafka topics) about state changes.
 */
public enum StorageEventType {

    /** Fired once when the storage layer has completed initialization and is ready to accept requests. */
    READY,

    /** An artifact was created. */
    ARTIFACT_CREATED,

    /** An artifact was deleted. */
    ARTIFACT_DELETED,

    /** An artifact's metadata was updated. */
    ARTIFACT_METADATA_UPDATED,

    /** A group was created. */
    GROUP_CREATED,

    /** A group was deleted. */
    GROUP_DELETED,

    /** A group's metadata was updated. */
    GROUP_METADATA_UPDATED,

    /** A new artifact version was created. */
    ARTIFACT_VERSION_CREATED,

    /** An artifact version's metadata was updated. */
    ARTIFACT_VERSION_METADATA_UPDATED,

    /** An artifact version was deleted. */
    ARTIFACT_VERSION_DELETED,

    /** A global rule was created, updated, or deleted. */
    GLOBAL_RULE_CONFIGURED,

    /** A group-level rule was created, updated, or deleted. */
    GROUP_RULE_CONFIGURED,

    /** An artifact-level rule was created, updated, or deleted. */
    ARTIFACT_RULE_CONFIGURED,

    /** An artifact version's lifecycle state was changed (e.g. ENABLED to DEPRECATED). */
    ARTIFACT_VERSION_STATE_CHANGED
}
