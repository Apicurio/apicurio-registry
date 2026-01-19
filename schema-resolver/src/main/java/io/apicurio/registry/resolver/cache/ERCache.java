package io.apicurio.registry.resolver.cache;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Expiration and Retry Cache
 */
public class ERCache<V> {

    private static final Logger log = LoggerFactory.getLogger(ERCache.class);

    private final Map<Long, WrappedValue<V>> globalIdIndex = new ConcurrentHashMap<>();
    private final Map<ContentWithReferences, WrappedValue<V>> contentIndex = new ConcurrentHashMap<>();
    private final Map<Long, WrappedValue<V>> contentIdIndex = new ConcurrentHashMap<>();
    private final Map<ArtifactCoordinates, WrappedValue<V>> gavIndex = new ConcurrentHashMap<>();
    private final Map<String, WrappedValue<V>> contentHashIndex = new ConcurrentHashMap<>();

    private Function<V, Long> globalIdExtractor;
    private Function<V, ContentWithReferences> contentExtractor;
    private Function<V, Long> contentIdExtractor;
    private Function<V, ArtifactCoordinates> gavExtractor;
    private Function<V, String> contentHashExtractor;

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
        this.globalIdExtractor = keyExtractor;
    }

    public void configureContentKeyExtractor(Function<V, ContentWithReferences> keyExtractor) {
        this.contentExtractor = keyExtractor;
    }

    public void configureContentIdKeyExtractor(Function<V, Long> keyExtractor) {
        this.contentIdExtractor = keyExtractor;
    }

    public void configureArtifactCoordinatesKeyExtractor(Function<V, ArtifactCoordinates> keyExtractor) {
        this.gavExtractor = keyExtractor;
    }

    public void configureContentHashKeyExtractor(Function<V, String> keyExtractor) {
        this.contentHashExtractor = keyExtractor;
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
        boolean initialized = globalIdExtractor != null && contentExtractor != null && contentIdExtractor != null
                && gavExtractor != null && contentHashExtractor != null;
        initialized = initialized && lifetime != null && backoff != null && retries >= 0;
        if (!initialized)
            throw new IllegalStateException("Not properly initialized!");
    }

    public boolean containsByGlobalId(Long key) {
        WrappedValue<V> value = this.globalIdIndex.get(key);
        return value != null && !value.isExpired();
    }

    public boolean containsByContentId(Long key) {
        WrappedValue<V> value = this.contentIdIndex.get(key);
        return value != null && !value.isExpired();
    }

    public boolean containsByArtifactCoordinates(ArtifactCoordinates key) {
        WrappedValue<V> value = this.gavIndex.get(key);
        return value != null && !value.isExpired();
    }

    public boolean containsByContentHash(String key) {
        WrappedValue<V> value = this.contentHashIndex.get(key);
        return value != null && !value.isExpired();
    }

    public V getByGlobalId(Long key, Function<Long, V> loaderFunction) {
        WrappedValue<V> value = this.globalIdIndex.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByContent(ContentWithReferences key, Function<ContentWithReferences, V> loaderFunction) {
        WrappedValue<V> value = this.contentIndex.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByContentId(Long key, Function<Long, V> loaderFunction) {
        WrappedValue<V> value = this.contentIdIndex.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByArtifactCoordinates(ArtifactCoordinates key,
                                      Function<ArtifactCoordinates, V> loaderFunction) {
        WrappedValue<V> value = this.gavIndex.get(key);
        return getValue(value, key, loaderFunction);
    }

    public V getByContentHash(String key, Function<String, V> loaderFunction) {
        WrappedValue<V> value = this.contentHashIndex.get(key);
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
        Optional.ofNullable(globalIdExtractor.apply(newValue.value)).ifPresent(k -> globalIdIndex.put(k, newValue));
        Optional.ofNullable(contentExtractor.apply(newValue.value)).ifPresent(k -> contentIndex.put(k, newValue));
        Optional.ofNullable(contentIdExtractor.apply(newValue.value)).ifPresent(k -> contentIdIndex.put(k, newValue));
        Optional.ofNullable(gavExtractor.apply(newValue.value)).ifPresent(k -> {
            gavIndex.put(k, newValue);
            // By storing the lookup key, we ensure that a null/latest lookup gets cached, as the key
            // extractor will
            // automatically add the version to the new key
            if (this.cacheLatest && k.getClass().equals(lookupKey.getClass())) {
                gavIndex.put((ArtifactCoordinates) lookupKey, newValue);
            }
        });
        Optional.ofNullable(contentHashExtractor.apply(newValue.value)).ifPresent(k -> contentHashIndex.put(k, newValue));
    }

    public void clear() {
        globalIdIndex.clear();
        contentIndex.clear();
        contentIdIndex.clear();
        gavIndex.clear();
        contentHashIndex.clear();
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
                // if (i == retries || !(e.getCause() != null && e.getCause() instanceof ExecutionException
                // && e.getCause().getCause() != null && e.getCause().getCause() instanceof ApiException
                // && (((ApiException) e.getCause().getCause()).getResponseStatusCode() == 429)))
                if (i == retries || !(e.getCause() != null && e.getCause() instanceof ApiException
                        && (((ApiException) e.getCause()).getResponseStatusCode() == 429))) {
                    log.error("Cache load failed after {} retries", i, e);
                    return Result.error(new RuntimeException(e));
                }
            }
            try {
                Thread.sleep(backoff.toMillis());
            } catch (InterruptedException e) {
                log.debug("Cache retry backoff interrupted", e);
                Thread.currentThread().interrupt();
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
