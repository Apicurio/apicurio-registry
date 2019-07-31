package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.KeyValueStorage;

public abstract class AbstractIMKeyValueStorage<K, V> extends AbstractROIMKeyValueStorage<K, V>
        implements KeyValueStorage<K, V> {

    @Override
    public void put(K key, V value) {
        storage.put(key, value);
    }

    @Override
    public void delete(K key) {
        storage.remove(key);
    }
}
