package io.apicurio.registry.streams.distore;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

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
     * @param filter the string filter
     * @param over   the search over enum name
     * @return filtered and limited stream
     */
    Stream<KeyValue<K, V>> filter(String filter, String over);
}
