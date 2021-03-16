package io.apicurio.registry.utils.streams.diservice;

import com.google.protobuf.ByteString;
import io.apicurio.registry.utils.streams.diservice.proto.AsyncBiFunctionServiceGrpc;
import io.apicurio.registry.utils.streams.diservice.proto.BiFunctionReq;
import io.apicurio.registry.utils.streams.diservice.proto.BiFunctionRes;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import org.apache.kafka.common.serialization.Serde;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

public class AsyncBiFunctionServiceGrpcClient<K, REQ, RES> implements AsyncBiFunctionService<K, REQ, RES> {

    private static final Logger logger = LoggerFactory.getLogger(AsyncBiFunctionServiceGrpcClient.class);

    private final String serviceName;
    private final Channel channel;
    private final AsyncBiFunctionServiceGrpc.AsyncBiFunctionServiceStub stub;
    private final Serde<K> keySerde;
    private final Serde<REQ> reqSerde;
    private final Serde<RES> resSerde;

    public AsyncBiFunctionServiceGrpcClient(
        String serviceName,
        Channel channel,
        Serde<K> keySerde,
        Serde<REQ> reqSerde,
        Serde<RES> resSerde
    ) {
        this.serviceName = serviceName;
        this.channel = channel;
        this.stub = AsyncBiFunctionServiceGrpc.newStub(channel);
        this.keySerde = keySerde;
        this.reqSerde = reqSerde;
        this.resSerde = resSerde;
    }

    // AutoCloseable

    @Override
    public void close() {
        if (channel instanceof ManagedChannel) {
            ((ManagedChannel) channel).shutdown();
        }
    }

    @Override
    public CompletionStage<RES> apply(K key, REQ req) {
        byte[] keyBytes = keySerde.serializer().serialize(serviceName, key);
        byte[] reqBytes = reqSerde.serializer().serialize(serviceName, req);
        ByteString keyByteStr = keyBytes == null ? ByteString.EMPTY : ByteString.copyFrom(keyBytes);
        ByteString reqByteStr = reqBytes == null ? ByteString.EMPTY : ByteString.copyFrom(reqBytes);

        logger.debug("Performing call through GRPC for key: {}", key);

        BiFunctionReq reqProto = BiFunctionReq
            .newBuilder()
            .setKey(keyByteStr)
            .setReq(reqByteStr)
            .setServiceName(serviceName)
            .build();

        StreamObserverCompletableFuture<BiFunctionRes> observerCF = new StreamObserverCompletableFuture<>();
        stub.apply(reqProto, observerCF);

        return observerCF
            .thenApply(
                resProto -> {
                    final RES responseDeserialized = resSerde.deserializer()
                            .deserialize(serviceName, resProto.getRes().isEmpty() ? null : resProto.getRes().toByteArray());
                    logger.debug("Response received: {} from grpc call for key: {}", responseDeserialized, key);
                    return responseDeserialized;
                }
            );
    }

    @Override
    public Stream<CompletionStage<RES>> applyForStore() {
        return apply();
    }

    @Override
    public Stream<CompletionStage<RES>> apply() {
        return Stream.of(apply(null, null));
    }
}
