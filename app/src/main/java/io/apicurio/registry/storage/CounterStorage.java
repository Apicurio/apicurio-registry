package io.apicurio.registry.storage;

/**
 * Storage for atomic counters.
 * TODO Does this need to be exposed to user?
 */
public interface CounterStorage {

    long DEAFULT_INITIAL_VALUE = 1; // TODO add def. method

    String DEFAULT_ID = "DEFAULT_ID";
    String ARTIFACT_SEQUENCE_ID = "ARTIFACT_SEQUENCE_ID";
    String ARTIFACT_ID = "ARTIFACT_ID";
    String ARTIFACT_SEQUENCE_VERSION_PREFIX = "ARTIFACT_SEQUENCE_VERSION_PREFIX";

    long getAndIncById(String counterKey, long initialValue);

    void delete(String counterKey);
}
