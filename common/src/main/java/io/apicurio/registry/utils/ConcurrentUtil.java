package io.apicurio.registry.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.currentThread;

public final class ConcurrentUtil {

    public static void blockOn(CompletableFuture<Void> cf) {
        blockOnInternal(() -> {
            cf.get();
            return null;
        });
    }

    public static <T> T blockOnResult(CompletableFuture<T> cf) {
        return blockOnInternal(cf::get);
    }

    private static <T> T blockOnInternal(CompletableFutureCallback<T> cf) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return cf.get();
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }
                    if (t instanceof Error) {
                        throw (Error) t;
                    }
                    throw new RuntimeException(e);
                }
            }
        } finally {
            if (interrupted) {
                currentThread().interrupt();
            }
        }
    }

    @FunctionalInterface
    private interface CompletableFutureCallback<T> {

        T get() throws InterruptedException, ExecutionException;
    }

    private ConcurrentUtil() {
    }
}
