package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.cache.ContentWithReferences;
import io.apicurio.registry.resolver.cache.ERCache;
import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ERCacheTest {

    @Test
    void testCheckInitializedPassesWithContentHashKeyExtractor() {
        ERCache<String> cache = newCache("foo key");

        cache.checkInitialized();
    }

    @Test
    void testCheckInitializedFailsWithoutContentHashKeyExtractor() {
        ERCache<Object> cache = new ERCache<>();

        Function<Object, Long> globalIdKeyExtractor = o -> 1L;
        Function<Object, Long> contentIdKeyExtractor = o -> 2L;
        Function<Object, ArtifactCoordinates> artifactKeyExtractor = o -> ArtifactCoordinates.builder().artifactId("artifact id").build();
        Function<Object, ContentWithReferences> contentKeyExtractor = o -> ContentWithReferences.builder().content("content").build();

        cache.configureGlobalIdKeyExtractor(globalIdKeyExtractor);
        cache.configureContentIdKeyExtractor(contentIdKeyExtractor);
        cache.configureArtifactCoordinatesKeyExtractor(artifactKeyExtractor);
        cache.configureContentKeyExtractor(contentKeyExtractor);

        assertThrows(IllegalStateException.class, cache::checkInitialized);
    }

    @Test
    void testContainsByContentHash() {
        String contentHashKey = "some key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {
            return "present";
        };

        assertFalse(cache.containsByContentHash(contentHashKey));
        cache.getByContentHash(contentHashKey, staticValueLoader);

        assertTrue(cache.containsByContentHash(contentHashKey));
    }

    @Test
    void testGetByContentHash() {
        String contentHashKey = "content hash key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {
            return "value";
        };
        Function<String, String> ensureCachedLoader = (key) -> {
            throw new IllegalStateException("this should've been cached");
        };

        String uncachedValue = cache.getByContentHash(contentHashKey, staticValueLoader);
        assertEquals("value", uncachedValue);
        assertDoesNotThrow(() -> {
            String cachedValue = cache.getByContentHash(contentHashKey, ensureCachedLoader);
            assertEquals("value", cachedValue);
        });
    }

    @Test
    void testGetByContentHashEnforcesTTL() {
        String contentHashKey = "content hash ttl key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ZERO);
        Function<String, String> firstLoader = (key) -> {
            return "a value";
        };
        Function<String, String> secondLoader = (key) -> {
            return "another value";
        };

        String firstValue = cache.getByContentHash(contentHashKey, firstLoader);
        assertEquals("a value", firstValue);
        String secondValue = cache.getByContentHash(contentHashKey, secondLoader);
        assertEquals("another value", secondValue);
    }

    @Test
    void testClearEmptiesContentHashIndex() {
        String contentHashKey = "another key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {
            return "some value";
        };
        cache.getByContentHash(contentHashKey, staticValueLoader);

        cache.clear();

        assertFalse(cache.containsByContentHash(contentHashKey));
    }

    @Test
    void testThrowsLoadExceptionsByDefault() {
        String contentHashKey = "another key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {
            throw new IllegalStateException("load failure");
        };

        assertThrows(RuntimeException.class, () -> {
            cache.getByContentHash(contentHashKey, staticValueLoader);
        });
    }

    @Test
    void testHoldsLoadExceptionsWhenFaultTolerantRefreshEnabled() {
        String contentHashKey = "another key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ZERO);
        cache.configureFaultTolerantRefresh(true);

        // Seed a value
        Function<String, String> workingLoader = (key) -> {
            return "some value";
        };
        String originalLoadValue = cache.getByContentHash(contentHashKey, workingLoader);

        // Refresh with a failing loader
        Function<String, String> failingLoader = (key) -> {
            throw new IllegalStateException("load failure");
        };
        String failingLoadValue = cache.getByContentHash(contentHashKey, failingLoader);

        assertEquals("some value", originalLoadValue);
        assertEquals("some value", failingLoadValue);
    }

    @Test
    void testCanCacheLatestWhenEnabled() {
        ERCache<String> cache = newCache("some key");
        cache.configureLifetime(Duration.ofMinutes(10));
        cache.configureCacheLatest(true);

        ArtifactCoordinates latestKey = new ArtifactCoordinates.ArtifactCoordinatesBuilder()
                .artifactId("someArtifactId").groupId("someGroupId").build();
        final AtomicInteger loadCount = new AtomicInteger(0);
        Function<ArtifactCoordinates, String> countingLoader = (key) -> {
            loadCount.incrementAndGet();
            return "some value";
        };

        // Seed a value
        String firstLookupValue = cache.getByArtifactCoordinates(latestKey, countingLoader);
        // Try the same lookup
        String secondLookupValue = cache.getByArtifactCoordinates(latestKey, countingLoader);

        assertEquals(firstLookupValue, secondLookupValue);
        assertEquals(1, loadCount.get());
    }

    @Test
    void doesNotCacheLatestWhenDisabled() {
        ERCache<String> cache = newCache("some key");
        cache.configureLifetime(Duration.ofMinutes(10));
        cache.configureCacheLatest(false);

        ArtifactCoordinates latestKey = new ArtifactCoordinates.ArtifactCoordinatesBuilder()
                .artifactId("someArtifactId").groupId("someGroupId").build();
        final AtomicInteger loadCount = new AtomicInteger(0);
        Function<ArtifactCoordinates, String> countingLoader = (key) -> {
            loadCount.incrementAndGet();
            return "some value";
        };

        // Seed a value
        String firstLookupValue = cache.getByArtifactCoordinates(latestKey, countingLoader);
        // Try the same lookup
        String secondLookupValue = cache.getByArtifactCoordinates(latestKey, countingLoader);

        assertEquals(firstLookupValue, secondLookupValue);
        assertEquals(2, loadCount.get());
    }

    @Test
    void testBackgroundRefreshReturnsStaleValueImmediately() throws InterruptedException {
        String contentHashKey = "background refresh key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ofMillis(100));
        cache.configureBackgroundRefresh(true);
        cache.configureBackgroundRefreshTimeout(Duration.ofSeconds(5));

        // Seed a value
        Function<String, String> firstLoader = (key) -> {
            return "initial value";
        };
        String initialValue = cache.getByContentHash(contentHashKey, firstLoader);
        assertEquals("initial value", initialValue);

        // Wait for expiration
        Thread.sleep(150);

        // Access with a slow loader - should return stale value immediately
        AtomicInteger loadCallCount = new AtomicInteger(0);
        Function<String, String> slowLoader = (key) -> {
            loadCallCount.incrementAndGet();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "refreshed value";
        };

        long startTime = System.currentTimeMillis();
        String staleValue = cache.getByContentHash(contentHashKey, slowLoader);
        long duration = System.currentTimeMillis() - startTime;

        // Should return stale value immediately (within 200ms)
        // The key behavior is that we get the stale value back quickly without blocking
        assertEquals("initial value", staleValue);
        assertTrue(duration < 200, "Should return immediately but took " + duration + "ms");

        // Note: We don't assert that loadCallCount is 0 because with a thread pool,
        // the background task may start executing immediately. What matters is that
        // getValue() returned quickly without blocking on the slow loader.

        // Clean up
        cache.shutdown();
    }

    @Test
    void testBackgroundRefreshEventuallyUpdatesCache() throws InterruptedException {
        String contentHashKey = "background refresh update key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ofMillis(100));
        cache.configureBackgroundRefresh(true);
        cache.configureBackgroundRefreshTimeout(Duration.ofSeconds(5));

        // Seed a value
        Function<String, String> firstLoader = (key) -> {
            return "original value";
        };
        cache.getByContentHash(contentHashKey, firstLoader);

        // Wait for expiration
        Thread.sleep(150);

        // Trigger background refresh
        Function<String, String> refreshLoader = (key) -> {
            return "updated value";
        };
        String staleValue = cache.getByContentHash(contentHashKey, refreshLoader);
        assertEquals("original value", staleValue);

        // Wait for background refresh to complete
        Thread.sleep(500);

        // Verify cache was updated - should not call loader again
        Function<String, String> ensureCachedLoader = (key) -> {
            throw new IllegalStateException("should be cached");
        };
        String updatedValue = cache.getByContentHash(contentHashKey, ensureCachedLoader);
        assertEquals("updated value", updatedValue);

        // Clean up
        cache.shutdown();
    }

    @Test
    void testBackgroundRefreshFallsBackToSyncWhenNoStaleValue() {
        String contentHashKey = "no stale value key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureBackgroundRefresh(true);

        // First load with no stale value - should be synchronous
        AtomicInteger loadCount = new AtomicInteger(0);
        Function<String, String> loader = (key) -> {
            loadCount.incrementAndGet();
            return "first load";
        };

        String value = cache.getByContentHash(contentHashKey, loader);

        assertEquals("first load", value);
        assertEquals(1, loadCount.get());

        // Clean up
        cache.shutdown();
    }

    @Test
    void testBackgroundRefreshPreventsConcurrentRefreshes() throws InterruptedException {
        String contentHashKey = "concurrent refresh key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ofMillis(100));
        cache.configureBackgroundRefresh(true);
        cache.configureBackgroundRefreshExecutorThreads(4);

        // Seed a value
        Function<String, String> seedLoader = (key) -> {
            return "original";
        };
        cache.getByContentHash(contentHashKey, seedLoader);

        // Wait for expiration
        Thread.sleep(150);

        // Multiple concurrent accesses with slow loader
        AtomicInteger loadCount = new AtomicInteger(0);
        Function<String, String> slowLoader = (key) -> {
            loadCount.incrementAndGet();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "refreshed";
        };

        // Simulate 5 concurrent requests
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                String value = cache.getByContentHash(contentHashKey, slowLoader);
                assertEquals("original", value);
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Wait for background refresh to complete
        Thread.sleep(700);

        // Only one background refresh should have been triggered
        assertEquals(1, loadCount.get());

        // Clean up
        cache.shutdown();
    }

    @Test
    void testBackgroundRefreshInteractionWithFaultTolerantRefresh() throws InterruptedException {
        String contentHashKey = "interaction key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ofMillis(100));
        cache.configureBackgroundRefresh(true);
        cache.configureFaultTolerantRefresh(true);

        // Seed a value
        Function<String, String> seedLoader = (key) -> {
            return "original";
        };
        cache.getByContentHash(contentHashKey, seedLoader);

        // Wait for expiration
        Thread.sleep(150);

        // Background refresh with failing loader should not throw
        Function<String, String> failingLoader = (key) -> {
            throw new IllegalStateException("background refresh failed");
        };

        // Should return stale value without throwing
        assertDoesNotThrow(() -> {
            String value = cache.getByContentHash(contentHashKey, failingLoader);
            assertEquals("original", value);
        });

        // Wait for background refresh attempt
        Thread.sleep(500);

        // Stale value should still be served
        Function<String, String> anotherLoader = (key) -> {
            return "should not be called";
        };
        String stillStaleValue = cache.getByContentHash(contentHashKey, anotherLoader);
        assertEquals("original", stillStaleValue);

        // Clean up
        cache.shutdown();
    }

    @Test
    void testBackgroundRefreshTimeout() throws InterruptedException {
        String contentHashKey = "timeout key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ofMillis(100));
        cache.configureBackgroundRefresh(true);
        cache.configureBackgroundRefreshTimeout(Duration.ofMillis(200));

        // Seed a value
        Function<String, String> seedLoader = (key) -> {
            return "original";
        };
        cache.getByContentHash(contentHashKey, seedLoader);

        // Wait for expiration
        Thread.sleep(150);

        // Trigger background refresh with loader that exceeds timeout
        Function<String, String> slowLoader = (key) -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
            return "should timeout";
        };

        String staleValue = cache.getByContentHash(contentHashKey, slowLoader);
        assertEquals("original", staleValue);

        // Wait for timeout to occur
        Thread.sleep(500);

        // Stale value should still be served (refresh timed out)
        String stillStale = cache.getByContentHash(contentHashKey, (key) -> "not called");
        assertEquals("original", stillStale);

        // Clean up
        cache.shutdown();
    }

    @Test
    void testShutdownCleansUpExecutor() throws InterruptedException {
        String contentHashKey = "shutdown key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ofMillis(100));
        cache.configureBackgroundRefresh(true);

        // Seed and trigger background refresh
        cache.getByContentHash(contentHashKey, (key) -> "value");
        Thread.sleep(150);
        cache.getByContentHash(contentHashKey, (key) -> "refreshed");

        // Shutdown should complete without hanging
        assertDoesNotThrow(() -> cache.shutdown());
    }

    private ERCache<String> newCache(String contentHashKey) {
        ERCache<String> cache = new ERCache<>();
        cache.configureLifetime(Duration.ofDays(30));

        Function<String, Long> globalIdKeyExtractor = o -> 1L;
        Function<String, Long> contentIdKeyExtractor = o -> 2L;
        Function<String, String> contentHashKeyExtractor = o -> contentHashKey;
        Function<String, ArtifactCoordinates> artifactKeyExtractor = o -> ArtifactCoordinates.builder().artifactId("artifact id").build();
        Function<String, ContentWithReferences> contentKeyExtractor = o -> ContentWithReferences.builder().content("content").build();

        cache.configureGlobalIdKeyExtractor(globalIdKeyExtractor);
        cache.configureContentIdKeyExtractor(contentIdKeyExtractor);
        cache.configureContentHashKeyExtractor(contentHashKeyExtractor);
        cache.configureArtifactCoordinatesKeyExtractor(artifactKeyExtractor);
        cache.configureContentKeyExtractor(contentKeyExtractor);

        return cache;
    }
}
