package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.CounterStorage;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Warning: This in-memory implementation does NOT support deployment in a cluster!
 */
@ApplicationScoped
@InMemory
public class IMCounterStorage implements CounterStorage {

    private final Map<String, AtomicLong> storage = new ConcurrentHashMap<>();

    @Override
    public long incrementAndGet(String key, long initialValue) {
        AtomicLong counter = storage.computeIfAbsent(key, k -> new AtomicLong(initialValue));
        return counter.incrementAndGet();
    }

    @Override
    public long incrementAndGet(String key) {
        return incrementAndGet(key, DEFAULT_INITIAL_VALUE);
    }

    @Override
    public void delete(String key) {
        storage.remove(key);
    }
}
