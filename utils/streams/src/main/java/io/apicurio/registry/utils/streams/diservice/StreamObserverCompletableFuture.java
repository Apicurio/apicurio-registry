package io.apicurio.registry.utils.streams.diservice;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class StreamObserverCompletableFuture<T> extends CompletableFuture<T> implements StreamObserver<T> {

    private static final Logger logger = LoggerFactory.getLogger(AsyncBiFunctionServiceGrpcLocalDispatcher.class);

    @Override
    public void onNext(T value) {
        complete(value);
    }

    @Override
    public void onError(Throwable throwable) {
        logger.error("Error in stream observer completable future", throwable);
        completeExceptionally(throwable);
    }

    @Override
    public void onCompleted() {
        // nothing (meant for mono results, so we can complete onNext() already)
    }
}
