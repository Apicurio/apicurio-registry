package io.apicurio.registry.utils.streams.distore;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Extend ReadOnlyKeyValueStore functionality a bit.
 * e.g. allKeys(), filterValues(), etc
 */
public interface ExtReadOnlyKeyValueStore<K, V> extends ReadOnlyKeyValueStore<K, V> {
    /**
     * All keys, as a stream.
     *
     * @return stream of all keys
     */
    Stream<K> allKeys();

    /**
     * Get filtered stream.
     *
     * @param filters to be used the string filter
     * @return filtered and limited stream
     */
    Stream<KeyValue<K, V>> filter(Map<String, String> filters);
}
