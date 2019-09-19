package io.apicurio.registry.streams.distore;

import org.apache.kafka.common.utils.CloseableIterator;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Stream to CloseableIterator
 */
public class StreamToIteratorAdapter<K> extends AbstractStreamToIteratorAdapter<K, K> {

    public StreamToIteratorAdapter(Stream<K> stream) {
        super(stream);
    }

    @Override
    protected K deserialize(K res) {
        return res;
    }

    public static <K> Stream<K> toStream(CloseableIterator<K> iter) {
        return StreamSupport
            .stream(
                Spliterators.spliteratorUnknownSize(
                    iter,
                    Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL
                ),
                false
            )
            .onClose(iter::close);
    }
}
