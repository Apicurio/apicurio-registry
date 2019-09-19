package io.apicurio.registry.streams.distore;

import org.apache.kafka.common.utils.CloseableIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Extend ReadOnlyKeyValueStore functionality a bit.
 * e.g. allKeys(), etc
 */
public interface ExtReadOnlyKeyValueStore<K, V> extends ReadOnlyKeyValueStore<K, V> {
    CloseableIterator<K> allKeys();
}
