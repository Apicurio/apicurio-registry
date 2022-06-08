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

package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import io.apicurio.registry.rest.client.exception.RateLimitedClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Expiration + Retry Cache
 *
 * @author Jakub Senko
 */
public class ERCache<V> {

    /**
     * ArtifactReference = Artifact reference
     * Long = Global ID
     * String = Content
     * Long = Content ID
     * ArtifactCoordinates = ArtifactCoordinates
     * V = Schema lookup result
     */
    private final Map<Long, WrappedValue<V>> index1 = new ConcurrentHashMap<>();
    private final Map<String, WrappedValue<V>> index2 = new ConcurrentHashMap<>();
    private final Map<Long, WrappedValue<V>> index3 = new ConcurrentHashMap<>();
    private final Map<ArtifactCoordinates, WrappedValue<V>> index4 = new ConcurrentHashMap<>();

    private Function<V, Long> keyExtractor1;
    private Function<V, String> keyExtractor2;
    private Function<V, Long> keyExtractor3;
    private Function<V, ArtifactCoordinates> keyExtractor4;

    private Duration lifetime = Duration.ZERO;
    private Duration backoff = Duration.ofMillis(200);
    private long retries;

    // === Configuration

    public void configureLifetime(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public void configureRetryBackoff(Duration backoff) {
        this.backoff = backoff;
    }

    public void configureRetryCount(long retries) {
        this.retries = retries;
    }

    public void configureGlobalIdKeyExtractor(Function<V, Long> keyExtractor) {
        this.keyExtractor1 = keyExtractor;
    }

    public void configureContentKeyExtractor(Function<V, String> keyExtractor) {
        this.keyExtractor2 = keyExtractor;
    }

    public void configureContentIdKeyExtractor(Function<V, Long> keyExtractor) {
        this.keyExtractor3 = keyExtractor;
    }

    public void configureArtifactCoordinatesKeyExtractor(Function<V, ArtifactCoordinates> keyExtractor) {
        this.keyExtractor4 = keyExtractor;
    }

    public void checkInitialized() {
        boolean initialized = keyExtractor1 != null && keyExtractor2 != null &&
            keyExtractor3 != null && keyExtractor4 != null;
        initialized = initialized && lifetime != null && backoff != null && retries >= 0;
        if (!initialized)
            throw new IllegalStateException("Not properly initialized!");
    }


    public boolean containsByGlobalId(Long key) {
        WrappedValue<V> value = this.index1.get(key);
        return value != null && !value.isExpired();
    }

    public boolean containsByContentId(Long key) {
        WrappedValue<V> value = this.index3.get(key);
        return value != null && !value.isExpired();
    }

    public boolean containsByArtifactCoordinates(ArtifactCoordinates key) {
        WrappedValue<V> value = this.index4.get(key);
        return value != null && !value.isExpired();
    }

    public V getByGlobalId(Long key, Function<Long, V> loaderFunction) {
        WrappedValue<V> value = this.index1.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByContent(String key, Function<String, V> loaderFunction) {
        WrappedValue<V> value = this.index2.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByContentId(Long key, Function<Long, V> loaderFunction) {
        WrappedValue<V> value = this.index3.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByArtifactCoordinates(ArtifactCoordinates key, Function<ArtifactCoordinates, V> loaderFunction) {
        WrappedValue<V> value = this.index4.get(key);
        return getValue(value, key, loaderFunction);
    }

    // === Generic

    private <T> V getValue(WrappedValue<V> value, T key, Function<T, V> loaderFunction) {
        V result = value != null ? value.value : null;

        if (value == null || value.isExpired()) {
            // With retry
            Result<V, RuntimeException> newValue = retry(backoff, retries, () -> {
                return loaderFunction.apply(key);
            });
            if (newValue.isOk()) {
                // Index
                reindex(new WrappedValue<>(lifetime, Instant.now(), newValue.ok));
                // Return
                result = newValue.ok;
            } else {
                throw newValue.error;
            }
        }

        return result;
    }

    private void reindex(WrappedValue<V> newValue) {
        Optional.ofNullable(keyExtractor1.apply(newValue.value)).ifPresent(k -> index1.put(k, newValue));
        Optional.ofNullable(keyExtractor2.apply(newValue.value)).ifPresent(k -> index2.put(k, newValue));
        Optional.ofNullable(keyExtractor3.apply(newValue.value)).ifPresent(k -> index3.put(k, newValue));
        Optional.ofNullable(keyExtractor4.apply(newValue.value)).ifPresent(k -> index4.put(k, newValue));
    }

    public void clear() {
        index1.clear();
        index2.clear();
        index3.clear();
        index4.clear();
    }

    // === Util & Other

    private static <T> Result<T, RuntimeException> retry(Duration backoff, long retries, Supplier<T> supplier) {
        if (retries < 0)
            throw new IllegalArgumentException();
        Objects.requireNonNull(supplier);
        Objects.requireNonNull(backoff);

        for (long i = 0; i <= retries; i++) {
            try {
                T value = supplier.get();
                if (value != null)
                    return Result.ok(value);
                else {
                    return Result.error(new NullPointerException("Could not retrieve schema for the cache. " +
                        "Loading function returned null."));
                }
            } catch (RuntimeException e) {
                // Rethrow the exception if we are not going to retry any more OR
                // the exception is NOT caused by throttling. This prevents
                // retries in cases where it does not make sense,
                // e.g. an ArtifactNotFoundException is thrown.
                // TODO Add additional exceptions that should cause a retry.
                if (i == retries || !(e instanceof RateLimitedClientException))
                    return Result.error(e);
            }
            try {
                Thread.sleep(backoff.toMillis());
            } catch (InterruptedException e) {
                // Ignore
                e.printStackTrace();
            }
        }
        return Result.error(new IllegalStateException("Unreachable."));
    }

    private static class WrappedValue<V> {

        private final Duration lifetime;
        private final Instant lastUpdate;
        private final V value;

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
    }

    public static class Result<T, E extends Exception> {

        public final T ok;
        public final E error;

        public static <T, E extends Exception> Result<T, E> ok(T ok) {
            Objects.requireNonNull(ok);
            return new Result<>(ok, null);
        }

        public static <T, E extends Exception> Result<T, E> error(E error) {
            Objects.requireNonNull(error);
            return new Result<>(null, error);
        }

        private Result(T ok, E error) {
            this.ok = ok;
            this.error = error;
        }

        public boolean isOk() {
            return this.ok != null;
        }

        public boolean isError() {
            return this.error != null;
        }
    }
}
