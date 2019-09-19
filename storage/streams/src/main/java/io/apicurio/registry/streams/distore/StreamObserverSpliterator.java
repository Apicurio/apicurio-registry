package io.apicurio.registry.streams.distore;

import io.grpc.stub.StreamObserver;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A {@link Spliterator} implementation that emits elements as they are received by the
 * gRPC client which calls into it with new data via {@link StreamObserver} interface.
 */
public class StreamObserverSpliterator<T> extends Spliterators.AbstractSpliterator<T> implements StreamObserver<T> {
    private static final Object EOS = new Object();

    public StreamObserverSpliterator() {
        super(
            Long.MAX_VALUE,
            Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL
        );
    }

    /**
     * @return a {@link Stream} that lazily binds to this {@link Spliterator} when evaluated.
     */
    public Stream<T> stream() {
        return StreamSupport.stream(
            () -> this,
            Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL,
            false
        );
    }

    private final BlockingQueue<Object> queue = new LinkedTransferQueue<>();

    // StreamObserver

    @Override
    public void onNext(T t) {
        queue.add(t);
    }

    @Override
    public void onError(Throwable throwable) {
        queue.add(new ExceptionWrapper(throwable));
    }

    @Override
    public void onCompleted() {
        queue.add(EOS);
    }

    // AbstractSpliterator

    private boolean finished;

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if (finished) return false;
        Object res;
        try {
            res = queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (res == EOS) {
            finished = true;
            return false;
        }
        if (res instanceof ExceptionWrapper) {
            finished = true;
            ((ExceptionWrapper) res).throwException();
            return false; // not reachable actually
        }
        @SuppressWarnings("unchecked")
        T t = (T) res;
        action.accept(t);
        return true;
    }

    private static class ExceptionWrapper {
        final Throwable exception;

        ExceptionWrapper(Throwable exception) {
            this.exception = exception;
        }

        void throwException() {
            if (exception instanceof RuntimeException) throw (RuntimeException) exception;
            if (exception instanceof Error) throw (Error) exception;
            throw new RuntimeException(exception);
        }
    }
}
