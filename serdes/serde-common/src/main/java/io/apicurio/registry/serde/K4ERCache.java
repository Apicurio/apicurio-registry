/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.serde;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Expiration + Retry Cache with 4 keys.
 *
 * @author Jakub Senko
 */
public class K4ERCache<K1, K2, K3, K4, V> {

    AtomicLong lastId = new AtomicLong(0);

    // TODO Add eviction?
    private final Map<Long, WrappedValue<V>> cache = new ConcurrentHashMap<>();

    // TODO Concurrent? vvv
    private final Map<K1, Long> index1 = new HashMap<>();
    private final Map<K2, Long> index2 = new HashMap<>();
    private final Map<K3, Long> index3 = new HashMap<>();
    private final Map<K4, Long> index4 = new HashMap<>();

    private BiFunction<K1, Object, V> loaderFunction1;
    private BiFunction<K2, Object, V> loaderFunction2;
    private BiFunction<K3, Object, V> loaderFunction3;
    private BiFunction<K4, Object, V> loaderFunction4;

    private Function<V, K1> keyExtractor1;
    private Function<V, K2> keyExtractor2;
    private Function<V, K3> keyExtractor3;
    private Function<V, K4> keyExtractor4;

    private Duration lifetime = Duration.ZERO;
    private Duration backoff = Duration.ofMillis(200);
    private int retries;

    // === Configuration

    public void configureLifetime(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public void configureRetryBackoff(Duration backoff) {
        this.backoff = backoff;
    }

    public void configureRetryCount(int retries) {
        this.retries = retries;
    }

    public void configureLoaderFunction1(BiFunction<K1, Object, V> loaderFunction) {
        this.loaderFunction1 = loaderFunction;
    }

    public void configureLoaderFunction2(BiFunction<K2, Object, V> loaderFunction) {
        this.loaderFunction2 = loaderFunction;
    }

    public void configureLoaderFunction3(BiFunction<K3, Object, V> loaderFunction) {
        this.loaderFunction3 = loaderFunction;
    }

    public void configureLoaderFunction4(BiFunction<K4, Object, V> loaderFunction) {
        this.loaderFunction4 = loaderFunction;
    }

    public void configureKeyExtractor1(Function<V, K1> keyExtractor) {
        this.keyExtractor1 = keyExtractor;
    }

    public void configureKeyExtractor2(Function<V, K2> keyExtractor) {
        this.keyExtractor2 = keyExtractor;
    }

    public void configureKeyExtractor3(Function<V, K3> keyExtractor) {
        this.keyExtractor3 = keyExtractor;
    }

    public void configureKeyExtractor4(Function<V, K4> keyExtractor) {
        this.keyExtractor4 = keyExtractor;
    }

    private void checkInitialized() {
        boolean initialized = keyExtractor1 != null && keyExtractor2 != null &&
            keyExtractor3 != null && keyExtractor4 != null;
        initialized = initialized && (loaderFunction1 != null || loaderFunction2 != null ||
            loaderFunction3 != null || loaderFunction4 != null);
        initialized = initialized && lifetime != null && backoff != null && retries >= 0;
        if (!initialized)
            throw new IllegalStateException("Not properly initialized!");
    }

    // === Specific

    public V get1(K1 key) {
        return get1(key, null);
    }

    public V get1(K1 key, Object context) {
        Long id = this.index1.get(key);
        // If id == null, we don't have the value in the cache,
        // because all key extractors have to work.
        if (id == null)
            id = this.lastId.incrementAndGet();
        return getValue(id, key, loaderFunction1, context);
    }

    public V get2(K2 key) {
        return get2(key, null);
    }

    public V get2(K2 key, Object context) {
        Long id = this.index2.get(key);
        // If id == null, we don't have the value in the cache,
        // because all key extractors have to work.
        if (id == null)
            id = this.lastId.incrementAndGet();
        return getValue(id, key, loaderFunction2, context);
    }

    public V get3(K3 key) {
        return get3(key, null);
    }

    public V get3(K3 key, Object context) {
        Long id = this.index3.get(key);
        // If id == null, we don't have the value in the cache,
        // because all key extractors have to work.
        if (id == null)
            id = this.lastId.incrementAndGet();
        return getValue(id, key, loaderFunction3, context);
    }

    public V get4(K4 key) {
        return get4(key, null);
    }

    public V get4(K4 key, Object context) {
        Long id = this.index4.get(key);
        // If id == null, we don't have the value in the cache,
        // because all key extractors have to work.
        if (id == null)
            id = this.lastId.incrementAndGet();
        return getValue(id, key, loaderFunction4, context);
    }

    // === Generic

    private <T> V getValue(Long id, T key, BiFunction<T, Object, V> loaderFunction, Object context) {
        checkInitialized();
        WrappedValue<V> value = cache.compute(id, (id0, wrappedValue) -> {
            if (wrappedValue == null) {
                // With retry
                Optional<V> newValue = retry(backoff, retries, () -> {
                    return loaderFunction.apply(key, context);
                });
                if (newValue.isPresent()) {
                    // Index
                    reindex(id, newValue.get());
                    // Return
                    return new WrappedValue<>(lifetime, Instant.now(), newValue.get());
                }
                return null;
            }
            if (wrappedValue.isExpired()) {
                // Retry and return stale if failed
                Optional<V> newValue = retry(backoff, retries, () -> {
                    return loaderFunction.apply(key, context);
                });
                if (newValue.isPresent()) {
                    // Reindex
                    reindex(id, newValue.get());
                    // Refresh
                    wrappedValue.refresh(newValue.get());
                }
            }
            return wrappedValue;
        });
        return value != null ? value.value : null;
    }

    private void reindex(Long id, V newValue) {
        Optional.ofNullable(keyExtractor1.apply(newValue)).ifPresent(k -> index1.put(k, id));
        Optional.ofNullable(keyExtractor2.apply(newValue)).ifPresent(k -> index2.put(k, id));
        Optional.ofNullable(keyExtractor3.apply(newValue)).ifPresent(k -> index3.put(k, id));
        Optional.ofNullable(keyExtractor4.apply(newValue)).ifPresent(k -> index4.put(k, id));
    }

    public void clear() {
        checkInitialized();
        cache.clear();
        index1.clear();
        index2.clear();
        index3.clear();
        index4.clear();
    }

    // === Util & Other

    private static void checkEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual))
            throw new IllegalStateException();
    }

    private static <T> Optional<T> retry(Duration backoff, int retries, Supplier<T> supplier) {
        if (retries < 0)
            throw new IllegalArgumentException();
        Objects.requireNonNull(supplier);
        Objects.requireNonNull(backoff);

        for (int i = 0; i <= retries; i++) {
            try {
                T value = supplier.get();
                if (value != null)
                    return Optional.of(value);
            } catch (Exception e) {
                e.printStackTrace(); // TODO Check for throttling and rethrow?
            }
            try {
                Thread.sleep(backoff.toMillis());
            } catch (InterruptedException e) {
                // Ignore
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    private static class WrappedValue<V> {

        private final Duration lifetime;
        private Instant lastUpdate;
        private V value;

        public WrappedValue(Duration lifetime, Instant lastUpdate, V value) {
            this.lifetime = lifetime;
            this.lastUpdate = lastUpdate;
            this.value = value;
        }

        public V getValue() {
            return value;
        }

        public boolean isExpired() {
            return lastUpdate.plus(lifetime).isBefore(Instant.now());
        }

        private void refresh() {
            lastUpdate = Instant.now();
        }

        public void refresh(V value) {
            refresh();
            this.value = value;
        }
    }

    public static class Tuple<T1, T2> {

        public final T1 v1;
        public final T2 v2;

        public Tuple(T1 v1, T2 v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }
}
