package io.apicurio.registry.storage.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.enterprise.context.ApplicationScoped;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class InMemoryRegistryStorage extends AbstractMapRegistryStorage {

    private AtomicLong counter = new AtomicLong(1);

    @Override
    protected long nextGlobalId() {
        return counter.getAndIncrement();
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
}
