package io.apicurio.registry.storage.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Ales Justin
 */
public abstract class SimpleMapRegistryStorage extends AbstractMapRegistryStorage {

    @Override
    protected Map<String, Map<Long, Map<String, String>>> createStorageMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Map<Long, Map<String, String>> createGlobalMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Map<String, String> createGlobalRulesMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Map<String, Map<String, String>> createArtifactRulesMap() {
        return new ConcurrentHashMap<>();
    }
}
