package io.apicurio.registry.storage;

/**
 * Simple read-only low-level key-value storage API.
 */
public interface ROKeyValueStorage<K, V> {

    /**
     * Retrieve a <em>value</em> from the storage using the associated <em>key</em>.
     *
     * @return null if the value was not found
     */
    V get(K key);
}
