package io.apicurio.registry.operator.utils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TraverseUtils {

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

    public static <T, R> R withReturn(T value, Function<T, R> action) {
        if (!isEmpty(value)) {
            return action.apply(value);
        }
        return null;
    }

    public static <T> void with(T value, Consumer<T> action) {
        withReturn(value, v -> {
            action.accept(v);
            return null;
        });
    }

    public static <T, R> R withOptReturn(Optional<T> value, Function<T, R> action) {
        if (!isEmpty(value)) {
            return action.apply(value.get());
        }
        return null;
    }

    public static <T> void withOpt(Optional<T> value, Consumer<T> action) {
        withOptReturn(value, v -> {
            action.accept(v);
            return null;
        });
    }

    public static <T> void where(List<T> source, Predicate<T> condition, Consumer<T> action) {
        if (source != null) {
            for (T item : source) {
                if (item != null && condition.test(item)) {
                    action.accept(item);
                }
            }
        }
    }

    public static <T> void where(List<T> source, int index, Consumer<T> action) {
        if (source != null && index < source.size()) {
            action.accept(source.get(index));
        }
    }

    public static <V> void mergeOverride(Map<String, V> target, Map<String, V> source) {
        if (target != null && source != null) {
            target.putAll(source);
        }
    }

    public static <T, K> void mergeOverride(List<T> target, List<T> source, Function<T, K> extractKey) {
        if (target != null && source != null) {
            for (T sval : source) {
                K skey = extractKey.apply(sval);
                for (int ti = 0; ti < target.size(); ti++) {
                    K tkey = extractKey.apply(target.get(ti));
                    if (skey.equals(tkey)) {
                        target.set(ti, sval);
                    }
                }
            }
        }
    }

    public static <V> void mergeNoOverride(Map<String, V> target, Map<String, V> source) {
        if (target != null && source != null) {
            source.forEach(target::putIfAbsent);
        }
    }

    public static <T, K> void mergeNoOverride(List<T> target, List<T> source, Function<T, K> extractKey) {
        if (target != null && source != null) {
            for (T sval : source) {
                K skey = extractKey.apply(sval);
                boolean skip = false;
                for (int ti = 0; ti < target.size(); ti++) {
                    K tkey = extractKey.apply(target.get(ti));
                    if (skey.equals(tkey)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    target.add(sval);
                }
            }
        }
    }
}
