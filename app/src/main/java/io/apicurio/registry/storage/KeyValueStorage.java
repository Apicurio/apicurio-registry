package io.apicurio.registry.storage;

/**
 * Simple low-level key-value storage API.
 * <p>
 * It may be implemented by an underlying key-value store directly,
 * or may represent an index into one.
 *
 * TODO create is tricky, should be unique for each case?
 */
public interface KeyValueStorage<K, V> extends ROKeyValueStorage<K, V> {

    void put(K key, V value);

    void delete(K key);
}
