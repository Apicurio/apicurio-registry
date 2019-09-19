package io.apicurio.registry.streams.distore;

import io.apicurio.registry.streams.distore.proto.Key;

import java.util.stream.Stream;

/**
 * Stream to CloseableIterator
 */
public class StreamToKeyIteratorAdapter<K> extends AbstractStreamToIteratorAdapter<Key, K> {
    private final KeyValueSerde<K, ?> kvSerde;

    public StreamToKeyIteratorAdapter(Stream<Key> stream, KeyValueSerde<K, ?> kvSerde) {
        super(stream);
        this.kvSerde = kvSerde;
    }

    @Override
    protected K deserialize(Key res) {
        return kvSerde.deserializeKey(res.toByteArray());
    }
}
