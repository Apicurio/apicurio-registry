package io.apicurio.registry.operator.utils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TraverseUtils {

    private TraverseUtils() {
    }

    public static boolean isEmpty(Object value) {
        if (value != null) {
            if (value instanceof Optional<?>) {
                return ((Optional<?>) value).isEmpty();
            }
            if (value instanceof String) {
                return ((String) value).isEmpty();
            }
            if (value instanceof Collection<?>) {
                return ((Collection<?>) value).isEmpty();
            }
            if (value instanceof Map<?, ?>) {
                return ((Map<?, ?>) value).isEmpty();
            }
            return false;
        }
        return true;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static <T> void where(List<T> source, Predicate<T> condition, Consumer<T> action) {
        whereR(source, condition, i -> {
            action.accept(i);
            return null;
        });
    }

    public static <T, R> R whereR(List<T> source, Predicate<T> condition, Function<T, R> action) {
        if (source != null) {
            for (T item : source) {
                if (item != null && condition.test(item)) {
                    return action.apply(item);
                }
            }
        }
        return null;
    }

    public static <T> void where(List<T> source, int index, Consumer<T> action) {
        if (source != null && index < source.size()) {
            action.accept(source.get(index));
        }
    }
}
