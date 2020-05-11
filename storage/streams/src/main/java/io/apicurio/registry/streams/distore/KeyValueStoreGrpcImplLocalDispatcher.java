package io.apicurio.registry.streams.distore;

import com.google.protobuf.ByteString;
import io.apicurio.registry.streams.distore.proto.*;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Concrete implementation of {@link KeyValueStoreGrpc.KeyValueStoreImplBase} that dispatches the
 * gRPC requests to the appropriately named local {@link ReadOnlyKeyValueStore}
 * {@link KafkaStreams#store obtained} from given {@link KafkaStreams} instance.
 */
public class KeyValueStoreGrpcImplLocalDispatcher extends KeyValueStoreGrpc.KeyValueStoreImplBase {

    private final KafkaStreams streams;
    private final KeyValueSerde.Registry keyValueSerdes;
    private final ConcurrentMap<String, ReadOnlyKeyValueStore<?, ?>> keyValueStores = new ConcurrentHashMap<>();
    private final FilterPredicate<?, ?> filterPredicate;

    public KeyValueStoreGrpcImplLocalDispatcher(
        KafkaStreams streams,
        KeyValueSerde.Registry keyValueSerdeRegistry,
        FilterPredicate<?, ?> filterPredicate
    ) {
        this.streams = streams;
        this.keyValueSerdes = keyValueSerdeRegistry;
        this.filterPredicate = filterPredicate;
    }

    @SuppressWarnings("unchecked")
    private <K, V> ExtReadOnlyKeyValueStore<K, V> keyValueStore(String storeName) {
        return (ExtReadOnlyKeyValueStore<K, V>)
            keyValueStores.computeIfAbsent(
                storeName,
                sn -> new ExtReadOnlyKeyValueStoreImpl<>(
                    streams.store(storeName, QueryableStoreTypes.keyValueStore()),
                    filterPredicate
                )
            );
    }

    @Override
    public void allKeys(VoidReq request, StreamObserver<Key> responseObserver) {
        boolean ok = false;
        try (Stream<?> stream = keyValueStore(request.getStoreName()).allKeys()) {
            drainToKey(request.getStoreName(), stream, responseObserver);
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void filter(FilterReq request, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        boolean ok = false;
        try (
            Stream stream = keyValueStore(request.getStoreName()).filter(request.getFilter(), request.getOver())
        ) {
            drainToKeyValue(request.getStoreName(), stream, responseObserver);
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void get(KeyReq request, StreamObserver<Value> responseObserver) {
        boolean ok = false;
        try {
            Object value = keyValueStore(request.getStoreName()).get(
                keyValueSerdes.deserializeKey(request.getStoreName(), request.getKey().toByteArray())
            );
            byte[] valueBytes = keyValueSerdes.serializeVal(request.getStoreName(), value);
            if (valueBytes != null) {
                responseObserver.onNext(
                    Value.newBuilder()
                        .setValue(ByteString.copyFrom(valueBytes))
                        .build()
                );
            }
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void range(KeyFromKeyToReq request, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        boolean ok = false;
        try (
            KeyValueIterator<?, ?> iter =
                keyValueStore(request.getStoreName()).range(
                    keyValueSerdes.deserializeKey(request.getStoreName(), request.getKeyFrom().toByteArray()),
                    keyValueSerdes.deserializeVal(request.getStoreName(), request.getKeyTo().toByteArray())
                )
        ) {
            drainToKeyValue(request.getStoreName(), iter, responseObserver);
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void all(VoidReq request, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        boolean ok = false;
        try (
            KeyValueIterator<?, ?> iter =
                keyValueStore(request.getStoreName()).all()
        ) {
            drainToKeyValue(request.getStoreName(), iter, responseObserver);
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void approximateNumEntries(VoidReq request, StreamObserver<Size> responseObserver) {
        boolean ok = false;
        try {
            long size = keyValueStore(request.getStoreName()).approximateNumEntries();
            responseObserver.onNext(Size.newBuilder().setSize(size).build());
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    private <K> void drainToKey(String storeName, Stream<K> stream, StreamObserver<io.apicurio.registry.streams.distore.proto.Key> responseObserver) {
        stream.forEach(key -> {
            byte[] keyBytes = keyValueSerdes.serializeKey(storeName, key);
            if (keyBytes != null) {
                responseObserver.onNext(
                    io.apicurio.registry.streams.distore.proto.Key
                        .newBuilder()
                        .setKey(ByteString.copyFrom(keyBytes))
                        .build()
                );
            }
        });
    }

    private <K, V> void drainToKeyValue(String storeName, Stream<KeyValue<K, V>> stream, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        stream.forEach(kv -> drainToKeyValue(storeName, kv, responseObserver));
    }

    private <K, V> void drainToKeyValue(String storeName, KeyValueIterator<K, V> iter, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        while (iter.hasNext()) {
            KeyValue<K, V> wkv = iter.next();
            drainToKeyValue(storeName, wkv, responseObserver);
        }
    }

    private <K, V> void drainToKeyValue(String storeName, KeyValue<K, V> wkv, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        byte[] keyBytes = keyValueSerdes.serializeKey(storeName, wkv.key);
        byte[] valueBytes = keyValueSerdes.serializeVal(storeName, wkv.value);
        if (keyBytes != null && valueBytes != null) {
            responseObserver.onNext(
                io.apicurio.registry.streams.distore.proto.KeyValue
                    .newBuilder()
                    .setKey(ByteString.copyFrom(keyBytes))
                    .setValue(ByteString.copyFrom(valueBytes))
                    .build()
            );
        }
    }
}
