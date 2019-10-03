package io.apicurio.registry.streams.distore;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An adapter that adapts {@link Stream}&lt;{@link KeyValue}&lt;K, V&gt;&gt to
 * {@link KeyValueIterator}&lt;K, V&gt; and lazily evaluates it when 1st queried.
 * <p>
 * It also contains a static method for converting it the other way.
 *
 * @see #toStream(KeyValueIterator)
 */
public class StreamToKeyValueIteratorAdapter<K, V> implements KeyValueIterator<K, V>,
                                                              Consumer<KeyValue<K, V>> {
    private final Stream<KeyValue<K, V>> stream;
    private Spliterator<KeyValue<K, V>> spliterator;

    public StreamToKeyValueIteratorAdapter(Stream<KeyValue<K, V>> stream) {
        this.stream = Objects.requireNonNull(stream);
    }

    private KeyValue<K, V> next;

    // Consumer<KeyValue<K, V>>

    @Override
    public void accept(KeyValue<K, V> kv) {
        next = Objects.requireNonNull(kv, "Stream should not contain null elements");
    }

    // KeyValueIterator<K, V>

    @Override
    public boolean hasNext() {
        if (next == null) {
            Spliterator<KeyValue<K, V>> spliterator = this.spliterator;
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
    public K peekNextKey() {
        if (!hasNext()) throw new NoSuchElementException();
        return next.key;
    }

    @Override
    public KeyValue<K, V> next() {
        if (!hasNext()) throw new NoSuchElementException();
        KeyValue<K, V> res = next;
        next = null;
        return res;
    }

    @Override
    public void close() {
        stream.close();
    }

    public static <K, V> Stream<KeyValue<K, V>> toStream(KeyValueIterator<K, V> kvIterator) {
        if (kvIterator instanceof StreamToKeyValueIteratorAdapter) {
            return ((StreamToKeyValueIteratorAdapter<K, V>) kvIterator).stream;
        }
        return StreamSupport
            .stream(
                Spliterators.spliteratorUnknownSize(
                    kvIterator,
                    Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL
                ),
                false
            )
            .onClose(kvIterator::close);
    }
}
