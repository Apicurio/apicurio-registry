package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.CounterStorage;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Warning: In-memory implementation violates the cluster-wide uniqueness requirement for counters!
 */
@ApplicationScoped
@InMemory
public class IMCounterStorage implements CounterStorage {

    private final Map<String, AtomicLong> storage = new ConcurrentHashMap<>();

    @Override
    public long getAndIncById(String counterKey, long initialValue) {
        AtomicLong counter = storage.computeIfAbsent(counterKey, key -> new AtomicLong(initialValue));
        return counter.incrementAndGet();
    }

    @Override
    public void delete(String counterKey) {
        storage.remove(counterKey);
    }
}
