package io.apicurio.registry.metrics;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Precomputes and caches the human-readable method signature string (e.g. {@code "getContentById(long)"})
 * used as a tag/attribute value by {@link StorageMetricsInterceptor} and {@link StorageTracingInterceptor}.
 * <p>
 * Building this string involves a {@link StringBuilder} and array iteration; since storage methods are
 * called on the hot path and {@link java.lang.reflect.Method} instances are stable across calls for a given
 * intercepted method, computing it once per {@link Method} and reusing the cached value avoids repeating
 * that allocation on every single storage call.
 */
final class StorageMethodSignatureCache {

    private static final ConcurrentHashMap<Method, String> CACHE = new ConcurrentHashMap<>();

    private StorageMethodSignatureCache() {
    }

    static String of(Method method) {
        return CACHE.computeIfAbsent(method, StorageMethodSignatureCache::build);
    }

    private static String build(Method method) {
        StringBuilder res = new StringBuilder(method.getName());
        res.append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            res.append(types[i].getSimpleName());
            if (i != types.length - 1) {
                res.append(',');
            }
        }
        res.append(')');
        return res.toString();
    }
}
