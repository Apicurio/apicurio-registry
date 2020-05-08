package io.apicurio.registry.streams.distore;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.stream.Stream;

/**
 * Local ExtReadOnlyKeyValueStore impl.
 */
public class ExtReadOnlyKeyValueStoreImpl<K, V> implements ExtReadOnlyKeyValueStore<K, V> {
    private final ReadOnlyKeyValueStore<K, V> delegate;
    private final TriPredicate<String, K, V> filterPredicate;

    public ExtReadOnlyKeyValueStoreImpl(ReadOnlyKeyValueStore<K, V> delegate, TriPredicate<String, K, V> filterPredicate) {
        this.delegate = delegate;
        this.filterPredicate = filterPredicate;
    }

    @Override
    public Stream<K> allKeys() {
        return StreamToKeyValueIteratorAdapter.toStream(all()).map(kv -> kv.key);
    }

    @Override
    public Stream<KeyValue<K, V>> filter(String filter, int limit) {
        return StreamToKeyValueIteratorAdapter.toStream(all())
            .limit(limit)
            .filter(kv -> filterPredicate.test(filter, kv.key, kv.value));
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
