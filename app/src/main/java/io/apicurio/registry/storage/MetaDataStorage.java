package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.MetaValue;

import java.util.Map;

/**
 * Object to store and access metadata of an artifact or artifactId.
 */
public interface MetaDataStorage extends KeyValueStorage<String, MetaValue> {

    String KEY_MODIFIED_ON = "MODIFIED_ON";
    String KEY_LATEST_ARTIFACT_VERSION = "LATEST_ARTIFACT_VERSION";

    Map<String, MetaValue> getAll();
}
