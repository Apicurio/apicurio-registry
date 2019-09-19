package io.apicurio.registry.streams.diservice;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.CompletableFuture;

public class StreamObserverCompletableFuture<T> extends CompletableFuture<T> implements StreamObserver<T> {
    @Override
    public void onNext(T value) {
        complete(value);
    }

    @Override
    public void onError(Throwable throwable) {
        completeExceptionally(throwable);
    }

    @Override
    public void onCompleted() {
        // nothing (meant for mono results, so we can complete onNext() already)
    }
}
