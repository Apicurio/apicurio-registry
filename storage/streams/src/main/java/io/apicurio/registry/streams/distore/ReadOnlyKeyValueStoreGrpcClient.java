package io.apicurio.registry.streams.distore;

import com.google.protobuf.ByteString;
import io.apicurio.registry.streams.distore.proto.*;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A gRPC based client of some remote {@link ReadOnlyKeyValueStore}.
 */
public class ReadOnlyKeyValueStoreGrpcClient<K, V> implements ExtReadOnlyKeyValueStore<K, V>, AutoCloseable {

    private final String storeName;
    private final Channel channel;
    private final KeyValueStoreGrpc.KeyValueStoreStub stub;
    private final KeyValueSerde<K, V> keyValueSerde;

    public ReadOnlyKeyValueStoreGrpcClient(
        String storeName,
        Channel channel,
        Serde<K> keySerde, Serde<V> valSerde
    ) {
        this.storeName = storeName;
        this.channel = channel;
        this.stub = KeyValueStoreGrpc.newStub(channel);
        this.keyValueSerde = new KeyValueSerde<>(storeName + "-serde-topic", keySerde, valSerde);
    }

    @Override
    public Stream<K> allKeys() {
        StreamObserverSpliterator<io.apicurio.registry.streams.distore.proto.Key> observer = new StreamObserverSpliterator<>();
        stub.allKeys(
            VoidReq.newBuilder()
                .setStoreName(storeName)
                .build(),
            observer
        );
        return observer.stream().map(res -> {
            ByteString key = res.getKey();
            return keyValueSerde.deserializeKey(key.toByteArray());
        });
    }

    @Override
    public Stream<KeyValue<K, V>> filter(String filter, String over) {
        StreamObserverSpliterator<io.apicurio.registry.streams.distore.proto.KeyValue> observer = new StreamObserverSpliterator<>();
        stub.filter(
            FilterReq
                .newBuilder()
                .setFilter(filter)
                .setOver(over)
                .setStoreName(storeName)
                .build(),
            observer
        );
        return keyValueStream(observer.stream());
    }

    // AutoCloseable

    @Override
    public void close() {
        if (channel instanceof ManagedChannel) {
            ((ManagedChannel) channel).shutdown();
        }
    }

    @Override
    public V get(K key) {
        ByteString keyBytes = ByteString.copyFrom(keyValueSerde.serializeKey(key));
        StreamObserverSpliterator<Value> observer = new StreamObserverSpliterator<>();
        stub.get(
            KeyReq
                .newBuilder()
                .setKey(keyBytes)
                .setStoreName(storeName)
                .build(),
            observer
        );
        return observer
            .stream()
            .map(value -> keyValueSerde.deserializeVal(value.getValue().toByteArray()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public KeyValueIterator<K, V> range(K from, K to) {
        ByteString fromBytes = ByteString.copyFrom(keyValueSerde.serializeKey(from));
        ByteString toBytes = ByteString.copyFrom(keyValueSerde.serializeKey(to));
        StreamObserverSpliterator<io.apicurio.registry.streams.distore.proto.KeyValue> observer = new StreamObserverSpliterator<>();
        stub.range(
            KeyFromKeyToReq
                .newBuilder()
                .setKeyFrom(fromBytes)
                .setKeyTo(toBytes)
                .setStoreName(storeName)
                .build(),
            observer
        );
        return keyValueIterator(observer.stream());
    }

    @Override
    public KeyValueIterator<K, V> all() {
        StreamObserverSpliterator<io.apicurio.registry.streams.distore.proto.KeyValue> observer = new StreamObserverSpliterator<>();
        stub.all(
            VoidReq.newBuilder()
                   .setStoreName(storeName)
                   .build(),
            observer
        );
        return keyValueIterator(observer.stream());
    }

    @Override
    public long approximateNumEntries() {
        StreamObserverSpliterator<io.apicurio.registry.streams.distore.proto.Size> observer = new StreamObserverSpliterator<>();
        stub.approximateNumEntries(
            VoidReq.newBuilder()
                   .setStoreName(storeName)
                   .build(),
            observer
        );
        return StreamSupport
            .stream(observer, false)
            .mapToLong(Size::getSize)
            .findFirst()
            .getAsLong();
    }

    private KeyValueIterator<K, V> keyValueIterator(Stream<io.apicurio.registry.streams.distore.proto.KeyValue> stream) {
        return new StreamToKeyValueIteratorAdapter<>(
            keyValueStream(stream)
        );
    }

    private Stream<KeyValue<K, V>> keyValueStream(Stream<io.apicurio.registry.streams.distore.proto.KeyValue> stream) {
        return stream
            .map(
                kv ->
                    new KeyValue<>(
                        keyValueSerde.deserializeKey(kv.getKey().toByteArray()),
                        keyValueSerde.deserializeVal(kv.getValue().toByteArray())
                    )
            );
    }
}
