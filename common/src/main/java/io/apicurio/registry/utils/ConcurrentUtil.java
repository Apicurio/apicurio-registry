/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.utils;

import java.util.concurrent.*;

/**
 * @author Ales Justin
 */
public class ConcurrentUtil {

    public static <T> T get(CompletableFuture<T> cf) {
        return get(cf, 0, null);
    }

    public static <T> T get(CompletableFuture<T> cf, long duration, TimeUnit unit) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return (duration <= 0) ? cf.get() : cf.get(duration, unit);
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException | TimeoutException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException)
                        throw (RuntimeException) t;
                    if (t instanceof Error) throw (Error) t;
                    throw new RuntimeException(e);
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
