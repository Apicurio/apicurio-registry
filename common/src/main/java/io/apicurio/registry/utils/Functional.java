package io.apicurio.registry.utils;

public final class Functional {

    private Functional() {
    }

    @FunctionalInterface
    public interface RunnableEx<X extends Exception> {

        void run() throws X;
    }

    @FunctionalInterface
    public interface Runnable1Ex<T, X extends Exception> {

        void run(T value) throws X;
    }

    public static <T, X extends Exception> Runnable1Ex<T, X> runnable1ExNoop() {
        return x -> {
        };
    }

    @FunctionalInterface
    public interface FunctionEx<R, X extends Exception> {

        R run() throws X;
    }

    @FunctionalInterface
    public interface Function1Ex<T, R, X extends Exception> {

        R run(T value) throws X;
    }
}
