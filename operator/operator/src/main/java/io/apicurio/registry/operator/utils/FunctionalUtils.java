package io.apicurio.registry.operator.utils;

import java.util.function.BiFunction;

public class FunctionalUtils {

    public static <T, R> BiFunction<T, R, R> returnSecondArg() {
        return (t, r) -> r;
    }
}
