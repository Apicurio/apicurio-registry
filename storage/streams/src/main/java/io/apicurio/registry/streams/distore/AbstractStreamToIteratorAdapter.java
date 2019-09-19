package io.apicurio.registry.streams.distore;

import org.apache.kafka.common.utils.CloseableIterator;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Stream to CloseableIterator
 */
public abstract class AbstractStreamToIteratorAdapter<I, O> implements CloseableIterator<O>, Consumer<I> {
    private final Stream<I> stream;
    private Spliterator<I> spliterator;

    public AbstractStreamToIteratorAdapter(Stream<I> stream) {
        this.stream = stream;
    }

    private I next;

    // Consumer<Key>

    @Override
    public void accept(I kv) {
        next = Objects.requireNonNull(kv, "Stream should not contain null elements");
    }

    // KeyValueIterator<K, V>

    @Override
    public boolean hasNext() {
        if (next == null) {
            Spliterator<I> spliterator = this.spliterator;
            if (spliterator == null) {
                // lazily obtain Spliterator (this is Stream terminal operation!)
                this.spliterator = spliterator = stream.spliterator();
            }
            return spliterator.tryAdvance(this);
        } else {
            return true;
        }
    }

    @Override
    public O next() {
        if (!hasNext()) throw new NoSuchElementException();
        I res = next;
        next = null;
        return deserialize(res);
    }

    protected abstract O deserialize(I res);

    @Override
    public void close() {
        stream.close();
    }
}
