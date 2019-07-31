package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.ROKeyValueStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractROIMKeyValueStorage<K, V> implements ROKeyValueStorage<K, V> {

    protected final Map<K, V> storage = new ConcurrentHashMap<>();

    @Override
    public V getByKey(K key) {
        return storage.get(key);
    }
}
