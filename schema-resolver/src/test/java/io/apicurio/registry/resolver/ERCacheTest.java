package io.apicurio.registry.resolver;

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
        Function<Object, Long> globalIdKeyExtractor = (o) -> {
            return 1L;
        };
        Function<Object, Long> contentIdKeyExtractor = (o) -> {
            return 2L;
        };
        Function<Object, ArtifactCoordinates> artifactKeyExtractor = (o) -> {
            return ArtifactCoordinates.builder().artifactId("artifact id").build();
        };
        Function<Object, String> contentKeyExtractor = (o) -> {
            return "content";
        };

        cache.configureGlobalIdKeyExtractor(globalIdKeyExtractor);
        cache.configureContentIdKeyExtractor(contentIdKeyExtractor);
        cache.configureArtifactCoordinatesKeyExtractor(artifactKeyExtractor);
        cache.configureContentKeyExtractor(contentKeyExtractor);

        assertThrows(IllegalStateException.class, () -> {
            cache.checkInitialized();
        });
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

    private ERCache<String> newCache(String contentHashKey) {
        ERCache<String> cache = new ERCache<>();
        cache.configureLifetime(Duration.ofDays(30));
        Function<String, Long> globalIdKeyExtractor = (o) -> {
            return 1L;
        };
        Function<String, Long> contentIdKeyExtractor = (o) -> {
            return 2L;
        };
        Function<String, String> contentHashKeyExtractor = (o) -> {
            return contentHashKey;
        };
        Function<String, ArtifactCoordinates> artifactKeyExtractor = (o) -> {
            return ArtifactCoordinates.builder().artifactId("artifact id").build();
        };
        Function<String, String> contentKeyExtractor = (o) -> {
            return "content";
        };

        cache.configureGlobalIdKeyExtractor(globalIdKeyExtractor);
        cache.configureContentIdKeyExtractor(contentIdKeyExtractor);
        cache.configureContentHashKeyExtractor(contentHashKeyExtractor);
        cache.configureArtifactCoordinatesKeyExtractor(artifactKeyExtractor);
        cache.configureContentKeyExtractor(contentKeyExtractor);

        return cache;
    }
}
