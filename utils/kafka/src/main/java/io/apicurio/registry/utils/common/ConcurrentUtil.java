package io.apicurio.registry.utils.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * @author Ales Justin
 */
public class ConcurrentUtil {

    public static <T> T get(CompletableFuture<T> cf) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return cf.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException)
                        throw (RuntimeException) t;
                    if (t instanceof Error) throw (Error) t;
                    throw new RuntimeException(t);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static <T> T result(CompletionStage<T> stage) {
        return get(stage.toCompletableFuture());
    }

    public static <T, R> Function<T, R> function(Function<T, R> function) {
        return function;
    }

    public static <T> Consumer<T> consumer(Consumer<T> consumer) {
        return consumer;
    }

    @FunctionalInterface
    public interface Function<T, R> extends java.util.function.Function<T, R> {
        @Override
        default R apply(T t) {
            boolean interrupted = false;
            while (true) {
                try {
                    return applyInterruptibly(t);
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        R applyInterruptibly(T t) throws InterruptedException;
    }

    @FunctionalInterface
    public interface Consumer<T> extends java.util.function.Consumer<T> {
        @Override
        default void accept(T t) {
            boolean interrupted = false;
            while (true) {
                try {
                    acceptInterruptibly(t);
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        void acceptInterruptibly(T t) throws InterruptedException;
    }
}
