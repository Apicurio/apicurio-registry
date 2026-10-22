package io.apicurio.registry.serde.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating bounded LRU (Least Recently Used) caches.
 * These caches are thread-safe and automatically evict the least recently accessed entry
 * when the cache exceeds the specified maximum size.
 */
public final class BoundedCacheFactory {

    private BoundedCacheFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a thread-safe bounded LRU cache.
     * When the cache exceeds maxSize, the least recently accessed entry is removed.
     *
     * @param <K> the type of keys maintained by this cache
     * @param <V> the type of mapped values
     * @param maxSize the maximum number of entries the cache should hold
     * @return a new thread-safe bounded LRU cache
     */
    public static <K, V> Map<K, V> createLRU(int maxSize) {
        return Collections.synchronizedMap(new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

}
