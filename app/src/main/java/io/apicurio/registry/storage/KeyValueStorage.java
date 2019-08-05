package io.apicurio.registry.storage;

/**
 * Simple low-level key-value storage API.
 * <p>
 * The <em>create</em> operation should be defined per-use-case,
 * depending on the method of generating <em>keys</em>.
 */
public interface KeyValueStorage<K, V> extends ROKeyValueStorage<K, V> {

    void put(K key, V value);

    void delete(K key);
}
