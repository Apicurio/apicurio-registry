package io.apicurio.registry.resolver;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Expiration + Retry Cache
 *
 * @type V SchemaLookupResult
 */
public class ERCache<V> {

    /** Global ID index */
    private final Map<Long, WrappedValue<V>> index1 = new ConcurrentHashMap<>();
    /** Data content index */
    private final Map<String, WrappedValue<V>> index2 = new ConcurrentHashMap<>();
    /** Artifact Content ID index */
    private final Map<Long, WrappedValue<V>> index3 = new ConcurrentHashMap<>();
    /** ArtifactCoordinates index */
    private final Map<ArtifactCoordinates, WrappedValue<V>> index4 = new ConcurrentHashMap<>();
    /** Artifact content hash index */
    private final Map<String, WrappedValue<V>> index5 = new ConcurrentHashMap<>();

    private Function<V, Long> keyExtractor1;
    private Function<V, String> keyExtractor2;
    private Function<V, Long> keyExtractor3;
    private Function<V, ArtifactCoordinates> keyExtractor4;
    private Function<V, String> keyExtractor5;

    private Duration lifetime = Duration.ZERO;
    private Duration backoff = Duration.ofMillis(200);
    private long retries;
    private boolean cacheLatest;
    private boolean faultTolerantRefresh;

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

    /**
     * If {@code true}, will cache schema lookups that either have `latest` or no version specified. Setting
     * this to false will effectively disable caching for schema lookups that do not specify a version.
     *
     * @param cacheLatest Whether to enable cache of artifacts without a version specified.
     */
    public void configureCacheLatest(boolean cacheLatest) {
        this.cacheLatest = cacheLatest;
    }

    /**
     * If set to {@code true}, will log the load error instead of throwing it when an exception occurs trying
     * to refresh a cache entry. This will still honor retries before enacting this behavior.
     *
     * @param faultTolerantRefresh Whether to enable fault tolerant refresh behavior.
     */
    public void configureFaultTolerantRefresh(boolean faultTolerantRefresh) {
        this.faultTolerantRefresh = faultTolerantRefresh;
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

    public void configureContentHashKeyExtractor(Function<V, String> keyExtractor) {
        this.keyExtractor5 = keyExtractor;
    }

    /**
     * Return whether caching of artifact lookups with {@code null} versions is enabled.
     *
     * @return {@code true} if it's enabled.
     * @see #configureCacheLatest(boolean)
     */
    public boolean isCacheLatest() {
        return this.cacheLatest;
    }

    /**
     * Return whether fault tolerant refresh is enabled.
     *
     * @return {@code true} if it's enabled.
     * @see #configureFaultTolerantRefresh(boolean)
     */
    public boolean isFaultTolerantRefresh() {
        return this.faultTolerantRefresh;
    }

    public void checkInitialized() {
        boolean initialized = keyExtractor1 != null && keyExtractor2 != null && keyExtractor3 != null
                && keyExtractor4 != null && keyExtractor5 != null;
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

    public boolean containsByContentHash(String key) {
        WrappedValue<V> value = this.index5.get(key);
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

    public V getByArtifactCoordinates(ArtifactCoordinates key,
            Function<ArtifactCoordinates, V> loaderFunction) {
        WrappedValue<V> value = this.index4.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByContentHash(String key, Function<String, V> loaderFunction) {
        WrappedValue<V> value = this.index5.get(key);
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
                reindex(new WrappedValue<>(lifetime, Instant.now(), newValue.ok), key);
                // Return
                result = newValue.ok;
            } else {
                if (faultTolerantRefresh && value != null) {
                    return value.value;
                }
                throw newValue.error;
            }
        }

        return result;
    }

    private <T> void reindex(WrappedValue<V> newValue, T lookupKey) {
        Optional.ofNullable(keyExtractor1.apply(newValue.value)).ifPresent(k -> index1.put(k, newValue));
        Optional.ofNullable(keyExtractor2.apply(newValue.value)).ifPresent(k -> index2.put(k, newValue));
        Optional.ofNullable(keyExtractor3.apply(newValue.value)).ifPresent(k -> index3.put(k, newValue));
        Optional.ofNullable(keyExtractor4.apply(newValue.value)).ifPresent(k -> {
            index4.put(k, newValue);
            // By storing the lookup key, we ensure that a null/latest lookup gets cached, as the key
            // extractor will
            // automatically add the version to the new key
            if (this.cacheLatest && k.getClass().equals(lookupKey.getClass())) {
                index4.put((ArtifactCoordinates) lookupKey, newValue);
            }
        });
        Optional.ofNullable(keyExtractor5.apply(newValue.value)).ifPresent(k -> index5.put(k, newValue));
    }

    public void clear() {
        index1.clear();
        index2.clear();
        index3.clear();
        index4.clear();
        index5.clear();
    }

    // === Util & Other

    private static <T> Result<T, RuntimeException> retry(Duration backoff, long retries,
            Supplier<T> supplier) {
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
                    return Result.error(new NullPointerException(
                            "Could not retrieve schema for the cache. " + "Loading function returned null."));
                }
            } catch (RuntimeException e) {
                // TODO: verify if this is really needed, retries are already baked into the adapter ...
                if (i == retries || !(e.getCause() != null && e.getCause() instanceof ExecutionException
                        && e.getCause().getCause() != null && e.getCause().getCause() instanceof ApiException
                        && (((ApiException) e.getCause().getCause()).getResponseStatusCode() == 429)))
                    return Result.error(new RuntimeException(e));
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
