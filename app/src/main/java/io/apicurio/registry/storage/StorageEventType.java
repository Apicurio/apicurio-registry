package io.apicurio.registry.storage;

public enum StorageEventType {

    /**
     * The READY event type MUST be fired only once.
     */
    READY, ARTIFACT_CREATED, ARTIFACT_DELETED, ARTIFACT_METADATA_UPDATED
}
