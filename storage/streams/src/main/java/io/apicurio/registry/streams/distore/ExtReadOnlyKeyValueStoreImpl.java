package io.apicurio.registry.streams.distore;

import org.apache.kafka.common.utils.CloseableIterator;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Local ExtReadOnlyKeyValueStore impl.
 */
public class ExtReadOnlyKeyValueStoreImpl<K, V> implements ExtReadOnlyKeyValueStore<K, V> {
    private final ReadOnlyKeyValueStore<K, V> delegate;

    public ExtReadOnlyKeyValueStoreImpl(ReadOnlyKeyValueStore<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public CloseableIterator<K> allKeys() {
        KeyValueIterator<K, V> iter = all();
        return new CloseableIterator<K>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public K next() {
                return iter.next().key;
            }

            @Override
            public void close() {
                iter.close();
            }
        };
    }

    @Override
    public V get(K key) {
        return delegate.get(key);
    }

    @Override
    public KeyValueIterator<K, V> range(K from, K to) {
        return delegate.range(from, to);
    }

    @Override
    public KeyValueIterator<K, V> all() {
        return delegate.all();
    }

    @Override
    public long approximateNumEntries() {
        return delegate.approximateNumEntries();
    }
}
