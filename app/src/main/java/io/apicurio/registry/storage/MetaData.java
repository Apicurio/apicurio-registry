package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.MetaValue;

import java.util.Map;

/**
 * Object to store and access metadata of an artifact or sequence.
 */
public interface MetaData extends KeyValueStorage<String, MetaValue> {

    // Well-known keys:
    String MODIFIED_ON = "modifiedOn";
    // etc...
    // TODO Some metadata should be protected from overwriting by users (in upper layers?)
    String META_LATEST_ARTIFACT_ID = "latestVersionId";

    Map<String, MetaValue> getAll();
}
