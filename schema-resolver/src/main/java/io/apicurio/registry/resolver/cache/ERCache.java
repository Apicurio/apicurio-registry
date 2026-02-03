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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private boolean backgroundRefresh;
    private long backgroundRefreshExecutorThreads = 2;
    private Duration backgroundRefreshTimeout = Duration.ofMillis(30000);

    // Background refresh state
    private volatile ExecutorService refreshExecutor;
    private final Map<Object, AtomicBoolean> refreshInProgress = new ConcurrentHashMap<>();

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

    /**
     * If set to {@code true}, enables background refresh of expired cache entries. When an entry expires and
     * a stale value exists, the cache will return the stale value immediately and trigger an asynchronous
     * refresh in the background. This follows the "stale-while-revalidate" pattern to prevent blocking on
     * cache refresh in high-concurrency scenarios. If no stale value exists (first fetch), falls back to
     * synchronous refresh.
     *
     * @param backgroundRefresh Whether to enable background refresh behavior.
     */
    public void configureBackgroundRefresh(boolean backgroundRefresh) {
        this.backgroundRefresh = backgroundRefresh;
    }

    /**
     * Configures the number of threads in the background refresh executor pool. Only used when background
     * refresh is enabled. The executor uses a fixed thread pool with daemon threads.
     *
     * @param threads The number of executor threads (must be positive).
     */
    public void configureBackgroundRefreshExecutorThreads(long threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Background refresh executor threads must be positive");
        }
        this.backgroundRefreshExecutorThreads = threads;
    }

    /**
     * Configures the timeout for background refresh operations. If a background refresh exceeds this
     * timeout, it will be interrupted and the stale value will continue to be served.
     *
     * @param timeout The refresh timeout duration (must be non-negative).
     */
    public void configureBackgroundRefreshTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Background refresh timeout must be non-negative");
        }
        this.backgroundRefreshTimeout = timeout;
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

    /**
     * Return whether background refresh is enabled.
     *
     * @return {@code true} if it's enabled.
     * @see #configureBackgroundRefresh(boolean)
     */
    public boolean isBackgroundRefresh() {
        return this.backgroundRefresh;
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
            // Background refresh: return stale value immediately and refresh asynchronously
            if (backgroundRefresh && value != null && value.isExpired()) {
                // Only trigger refresh if not already in progress
                AtomicBoolean refreshFlag = refreshInProgress.computeIfAbsent(key, k -> new AtomicBoolean(false));
                if (refreshFlag.compareAndSet(false, true)) {
                    scheduleBackgroundRefresh(key, loaderFunction);
                }
                // Return stale value immediately (non-blocking)
                return value.value;
            }

            // Synchronous refresh (original behavior)
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

    /**
     * Shuts down the background refresh executor if it exists. Waits for currently executing tasks to
     * complete with a timeout. This method should be called when the cache is no longer needed to ensure
     * proper cleanup of background threads.
     */
    public void shutdown() {
        if (refreshExecutor != null) {
            refreshExecutor.shutdown();
            try {
                if (!refreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    refreshExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                refreshExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // === Util & Other

    /**
     * Schedules a background refresh for the given key using the loader function. The refresh is executed
     * asynchronously with a timeout. On successful refresh, the cache is updated. On failure, the error is
     * logged and the stale value continues to be served.
     */
    private <T> void scheduleBackgroundRefresh(T key, Function<T, V> loaderFunction) {
        ExecutorService executor = getOrCreateRefreshExecutor();
        Future<?> refreshFuture = executor.submit(() -> {
            try {
                log.debug("Background refresh started for key: {}", key);
                // Use same retry logic as synchronous refresh
                Result<V, RuntimeException> newValue = retry(backoff, retries, () -> {
                    return loaderFunction.apply(key);
                });
                if (newValue.isOk()) {
                    reindex(new WrappedValue<>(lifetime, Instant.now(), newValue.ok), key);
                    log.debug("Background refresh completed successfully for key: {}", key);
                } else {
                    log.warn("Background refresh failed for key: {}", key, newValue.error);
                }
            } catch (Exception e) {
                log.warn("Background refresh encountered unexpected error for key: {}", key, e);
            } finally {
                // Always clear the refresh flag when done
                refreshInProgress.remove(key);
            }
        });

        // Monitor the future with timeout
        executor.submit(() -> {
            try {
                refreshFuture.get(backgroundRefreshTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Background refresh timed out for key: {} after {}ms", key, backgroundRefreshTimeout.toMillis());
                refreshFuture.cancel(true);
            } catch (Exception e) {
                log.debug("Background refresh monitoring interrupted for key: {}", key, e);
            }
        });
    }

    /**
     * Lazily initializes and returns the background refresh executor. Uses a fixed thread pool with daemon
     * threads to prevent blocking application shutdown.
     */
    private ExecutorService getOrCreateRefreshExecutor() {
        if (refreshExecutor == null) {
            synchronized (this) {
                if (refreshExecutor == null) {
                    refreshExecutor = Executors.newFixedThreadPool((int) backgroundRefreshExecutorThreads, r -> {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true);
                        thread.setName("ercache-refresh-" + thread.getId());
                        return thread;
                    });
                    log.debug("Background refresh executor initialized with {} threads", backgroundRefreshExecutorThreads);
                }
            }
        }
        return refreshExecutor;
    }

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
