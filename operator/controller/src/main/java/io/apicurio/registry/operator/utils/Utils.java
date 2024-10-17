package io.apicurio.registry.operator.utils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class Utils {

    private Utils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    /**
     * Merge source map into target map, overriding any entries with the same key.
     *
     * @param target must not be null
     */
    public static <V> void mergeOverride(Map<String, V> target, Map<String, V> source) {
        requireNonNull(target);
        if (source != null) {
            target.putAll(source);
        }
    }

    /**
     * Merge source list into target list, overriding any items based on the equality of the extracted key.
     *
     * @param target must not be null
     */
    public static <T, K> void mergeOverride(List<T> target, List<T> source, Function<T, K> extractKey) {
        requireNonNull(target);
        if (source != null) {
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

    /**
     * Merge source map into target map, NOT overriding any entries with the same key.
     *
     * @param target must not be null
     */
    public static <V> void mergeNotOverride(Map<String, V> target, Map<String, V> source) {
        requireNonNull(target);
        if (source != null) {
            source.forEach(target::putIfAbsent);
        }
    }

    /**
     * Merge source list into target list, NOT overriding any items based on the equality of the extracted
     * key.
     *
     * @param target must not be null
     */
    public static <T, K> void mergeNotOverride(List<T> target, List<T> source, Function<T, K> extractKey) {
        requireNonNull(target);
        if (source != null) {
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
