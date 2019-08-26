package io.apicurio.registry.kafka;

import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaRegistryStorage extends AbstractMapRegistryStorage {
    private long counter = 0;

    @Override
    protected long nextGlobalId() {
        return ++counter;
    }

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
