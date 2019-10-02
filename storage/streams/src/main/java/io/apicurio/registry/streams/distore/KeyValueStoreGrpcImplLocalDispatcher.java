package io.apicurio.registry.streams.distore;

import com.google.protobuf.ByteString;
import io.apicurio.registry.streams.distore.proto.Key;
import io.apicurio.registry.streams.distore.proto.KeyFromKeyToReq;
import io.apicurio.registry.streams.distore.proto.KeyReq;
import io.apicurio.registry.streams.distore.proto.KeyValueStoreGrpc;
import io.apicurio.registry.streams.distore.proto.Size;
import io.apicurio.registry.streams.distore.proto.Value;
import io.apicurio.registry.streams.distore.proto.VoidReq;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.common.utils.CloseableIterator;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concrete implementation of {@link KeyValueStoreGrpc.KeyValueStoreImplBase} that dispatches the
 * gRPC requests to the appropriately named local {@link ReadOnlyKeyValueStore}
 * {@link KafkaStreams#store obtained} from given {@link KafkaStreams} instance.
 */
public class KeyValueStoreGrpcImplLocalDispatcher extends KeyValueStoreGrpc.KeyValueStoreImplBase {

    private final KafkaStreams streams;
    private final KeyValueSerde.Registry keyValueSerdes;
    private final ConcurrentMap<String, ReadOnlyKeyValueStore<?, ?>> keyValueStores = new ConcurrentHashMap<>();

    public KeyValueStoreGrpcImplLocalDispatcher(
        KafkaStreams streams,
        KeyValueSerde.Registry keyValueSerdeRegistry
    ) {
        this.streams = streams;
        this.keyValueSerdes = keyValueSerdeRegistry;
    }

    @SuppressWarnings("unchecked")
    private <K, V> ExtReadOnlyKeyValueStore<K, V> keyValueStore(String storeName) {
        return (ExtReadOnlyKeyValueStore<K, V>)
            keyValueStores.computeIfAbsent(
                storeName,
                sn -> new ExtReadOnlyKeyValueStoreImpl<>(streams.store(storeName, QueryableStoreTypes.keyValueStore()))
            );
    }

    @Override
    public void allKeys(VoidReq request, StreamObserver<Key> responseObserver) {
        boolean ok = false;
        try (CloseableIterator<?> iter = this.keyValueStore(request.getStoreName()).allKeys()) {
            drainTo(request.getStoreName(), iter, responseObserver);
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
                this.keyValueStore(request.getStoreName()).range(
                    keyValueSerdes.deserializeKey(request.getStoreName(), request.getKeyFrom().toByteArray()),
                    keyValueSerdes.deserializeVal(request.getStoreName(), request.getKeyTo().toByteArray())
                )
        ) {
            drainTo(request.getStoreName(), iter, responseObserver);
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
                this.keyValueStore(request.getStoreName()).all()
        ) {
            drainTo(request.getStoreName(), iter, responseObserver);
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
            long size = this.keyValueStore(request.getStoreName()).approximateNumEntries();
            responseObserver.onNext(Size.newBuilder().setSize(size).build());
            ok = true;
        } catch (Throwable e) {
            responseObserver.onError(e);
        }
        if (ok) {
            responseObserver.onCompleted();
        }
    }

    private <K> void drainTo(String storeName, Iterator<K> iter, StreamObserver<io.apicurio.registry.streams.distore.proto.Key> responseObserver) {
        while (iter.hasNext()) {
            byte[] keyBytes = keyValueSerdes.serializeKey(storeName, iter.next());
            if (keyBytes != null) {
                responseObserver.onNext(
                    io.apicurio.registry.streams.distore.proto.Key
                        .newBuilder()
                        .setKey(ByteString.copyFrom(keyBytes))
                        .build()
                );
            }
        }
    }

    private <K, V> void drainTo(String storeName, KeyValueIterator<K, V> iter, StreamObserver<io.apicurio.registry.streams.distore.proto.KeyValue> responseObserver) {
        while (iter.hasNext()) {
            KeyValue<K, V> wkv = iter.next();
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
}
