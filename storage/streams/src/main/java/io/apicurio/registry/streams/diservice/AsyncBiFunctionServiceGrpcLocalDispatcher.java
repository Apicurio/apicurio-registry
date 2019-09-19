package io.apicurio.registry.streams.diservice;

import com.google.protobuf.ByteString;
import io.apicurio.registry.streams.diservice.proto.AsyncBiFunctionServiceGrpc;
import io.apicurio.registry.streams.diservice.proto.BiFunctionReq;
import io.apicurio.registry.streams.diservice.proto.BiFunctionRes;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Collection;

public class AsyncBiFunctionServiceGrpcLocalDispatcher extends AsyncBiFunctionServiceGrpc.AsyncBiFunctionServiceImplBase {
    private final LocalService.Registry<? extends AsyncBiFunctionService.WithSerdes<?, ?, ?>> localServiceRegistry;

    public AsyncBiFunctionServiceGrpcLocalDispatcher(
        Collection<LocalService<? extends AsyncBiFunctionService.WithSerdes<?, ?, ?>>> localAsyncBiFunctionServices
    ) {
        this.localServiceRegistry = new LocalService.Registry<>(localAsyncBiFunctionServices);
    }

    @Override
    public void apply(BiFunctionReq request, StreamObserver<BiFunctionRes> responseObserver) {
        String serviceName = request.getServiceName();
        AsyncBiFunctionService.WithSerdes<Object, Object, Object> localService;
        try {
            // need to work in Object domain since this dispatcher is used for multiple local services of
            // the same type but possibly different type parameter(s). Type safety is guaranteed nevertheless
            // since distributed service is always initialized with a local service in a type-safe fashion
            // together with a chosen service name. Service name therefore guarantees correct type parameters.
            @SuppressWarnings("unchecked")
            AsyncBiFunctionService.WithSerdes<Object, Object, Object> _abfs =
                (AsyncBiFunctionService.WithSerdes) localServiceRegistry.get(serviceName);
            localService = _abfs;
        } catch (Exception e) {
            responseObserver.onError(e);
            return;
        }

        Object key = localService.keySerde().deserializer().deserialize(serviceName, request.getKey().isEmpty() ? null : request.getKey().toByteArray());
        Object req = localService.reqSerde().deserializer().deserialize(serviceName, request.getReq().isEmpty() ? null : request.getReq().toByteArray());
        Serializer<Object> resSerializer = localService.resSerde().serializer();

        try {
            localService
                .apply(key, req)
                .whenComplete((res, serviceExc) -> {
                    if (serviceExc != null) {
                        responseObserver.onError(serviceExc);
                    } else {
                        BiFunctionRes resProto = null;
                        try {
                            byte[] resBytes = resSerializer.serialize(serviceName, res);
                            resProto = BiFunctionRes
                                .newBuilder()
                                .setRes(resBytes == null ? ByteString.EMPTY : ByteString.copyFrom(resBytes))
                                .build();
                        } catch (Throwable serializeExc) {
                            responseObserver.onError(serializeExc);
                        }
                        if (resProto != null) {
                            responseObserver.onNext(resProto);
                            responseObserver.onCompleted();
                        }
                    }
                });
        } catch (Throwable applyExc) {
            responseObserver.onError(applyExc);
        }
    }
}
