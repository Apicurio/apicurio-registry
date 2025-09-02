package io.apicurio.registry.cli.utils;

public final class Utils {


    private Utils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    public interface Function0<T> {

        void run(T value);
    }

    @FunctionalInterface
    public interface Function0Ex<T, X extends Exception> {

        void run(T value) throws X;
    }
}
