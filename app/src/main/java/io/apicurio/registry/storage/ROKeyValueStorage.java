package io.apicurio.registry.storage;

/**
 * Simple read-only low-level key-value storage API.
 * <p>
 * It may be implemented by an underlying key-value store directly,
 * or may represent an index into one.
 */
public interface ROKeyValueStorage<K, V> {

    V getByKey(K key);
}
