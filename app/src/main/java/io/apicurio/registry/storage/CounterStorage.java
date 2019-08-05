package io.apicurio.registry.storage;

/**
 * Storage for atomic counters.
 * <p>
 * Counters MUST be atomic even if the application is deployed in a cluster!
 * <p>
 * TODO Add locks?
 */
public interface CounterStorage {

    long DEFAULT_INITIAL_VALUE = 0;

    String DEFAULT_ID = "DEFAULT_ID";

    String ARTIFACT_ID = "ARTIFACT_ID";
    //String ARTIFACT_SEQUENCE_ID = "ARTIFACT_SEQUENCE_ID";
    String ARTIFACT_VERSION_ID_PREFIX = "ARTIFACT_VERSION_ID_PREFIX:";

    long incrementAndGet(String key);

    long incrementAndGet(String key, long initialValue);

    void delete(String key);
}
