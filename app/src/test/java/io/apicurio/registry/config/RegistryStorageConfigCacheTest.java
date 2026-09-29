package io.apicurio.registry.config;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegistryStorageConfigCache}: an in-memory cache of dynamic config property
 * reads, invalidated on local writes and refreshed periodically (bounded, for cross-replica
 * consistency) - see the class javadoc/{@link io.apicurio.registry.storage.decorator.RegistryStorageDecoratorOrderConstants}.
 */
public class RegistryStorageConfigCacheTest {

    private RegistryStorage delegate;
    private RegistryStorageConfigCache cache;

    @BeforeEach
    void setUp() {
        delegate = mock(RegistryStorage.class);

        cache = new RegistryStorageConfigCache();
        cache.enabled = true;
        cache.log = LoggerFactory.getLogger(RegistryStorageConfigCacheTest.class);
        cache.setDelegate(delegate);
    }

    @Test
    void getConfigPropertyCachesAcrossRepeatedCalls() {
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto("apicurio.some.property", "value");
        when(delegate.getConfigProperty("apicurio.some.property")).thenReturn(property);

        DynamicConfigPropertyDto first = cache.getConfigProperty("apicurio.some.property");
        DynamicConfigPropertyDto second = cache.getConfigProperty("apicurio.some.property");

        assertEquals(property, first);
        assertEquals(property, second);
        verify(delegate, times(1)).getConfigProperty("apicurio.some.property");
    }

    @Test
    void getConfigPropertyCachesNotFoundResultToo() {
        when(delegate.getConfigProperty("apicurio.missing.property")).thenReturn(null);

        DynamicConfigPropertyDto first = cache.getConfigProperty("apicurio.missing.property");
        DynamicConfigPropertyDto second = cache.getConfigProperty("apicurio.missing.property");

        assertNull(first);
        assertNull(second);
        // The "not found" result must be cached too (a NULL_DTO sentinel internally), not just the
        // present-value case, otherwise a property that legitimately has no override configured
        // would hit storage on every single read forever.
        verify(delegate, times(1)).getConfigProperty("apicurio.missing.property");
    }

    @Test
    void setConfigPropertyInvalidatesTheWholeCache() {
        DynamicConfigPropertyDto propertyA = new DynamicConfigPropertyDto("apicurio.a", "1");
        DynamicConfigPropertyDto propertyB = new DynamicConfigPropertyDto("apicurio.b", "1");
        when(delegate.getConfigProperty("apicurio.a")).thenReturn(propertyA);
        when(delegate.getConfigProperty("apicurio.b")).thenReturn(propertyB);

        // Warm the cache for both properties.
        cache.getConfigProperty("apicurio.a");
        cache.getConfigProperty("apicurio.b");
        verify(delegate, times(1)).getConfigProperty("apicurio.a");
        verify(delegate, times(1)).getConfigProperty("apicurio.b");

        DynamicConfigPropertyDto updatedA = new DynamicConfigPropertyDto("apicurio.a", "2");
        cache.setConfigProperty(updatedA);
        verify(delegate).setConfigProperty(updatedA);

        // Both entries must be invalidated (not just the one that was written), since the cache
        // invalidates in bulk rather than per-key.
        when(delegate.getConfigProperty("apicurio.a")).thenReturn(updatedA);
        cache.getConfigProperty("apicurio.a");
        cache.getConfigProperty("apicurio.b");
        verify(delegate, times(2)).getConfigProperty("apicurio.a");
        verify(delegate, times(2)).getConfigProperty("apicurio.b");
    }

    @Test
    void scheduledRefreshInvalidatesCacheWhenStalePropertiesAreDetected() {
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto("apicurio.a", "1");
        when(delegate.getConfigProperty("apicurio.a")).thenReturn(property);
        when(delegate.isReady()).thenReturn(true);

        // Warm the cache, then run the scheduled refresh once so lastRefresh is set to a non-null
        // instant (the first run only records a baseline timestamp - see class javadoc/refresh()).
        cache.getConfigProperty("apicurio.a");
        cache.run();
        verify(delegate, times(1)).getConfigProperty("apicurio.a");

        // No stale properties: cache must NOT be invalidated.
        when(delegate.getStaleConfigProperties(any(Instant.class))).thenReturn(List.of());
        cache.run();
        cache.getConfigProperty("apicurio.a");
        verify(delegate, times(1)).getConfigProperty("apicurio.a");

        // A stale property reported by storage (e.g. another replica wrote a new value): cache
        // must be invalidated, so the next read goes back to storage.
        when(delegate.getStaleConfigProperties(any(Instant.class))).thenReturn(List.of(property));
        cache.run();
        cache.getConfigProperty("apicurio.a");
        verify(delegate, times(2)).getConfigProperty("apicurio.a");
    }

    @Test
    void scheduledRefreshDoesNothingWhenDisabled() {
        cache.enabled = false;

        cache.run();

        verify(delegate, times(0)).isReady();
        verify(delegate, times(0)).getStaleConfigProperties(any(Instant.class));
    }

    @Test
    void isEnabledReflectsConfigProperty() {
        cache.enabled = true;
        assertEquals(true, cache.isEnabled());

        cache.enabled = false;
        assertEquals(false, cache.isEnabled());
    }

    @Test
    void getConfigPropertyDoesNotCacheAValueLoadedAcrossAnInvalidation() throws Exception {
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch invalidated = new CountDownLatch(1);
        AtomicReference<String> stored = new AtomicReference<>("old");

        when(delegate.getConfigProperty("apicurio.a")).thenAnswer(invocation -> {
            String snapshot = stored.get();
            loadStarted.countDown();
            assertTrue(invalidated.await(10, TimeUnit.SECONDS), "invalidation did not happen");
            return new DynamicConfigPropertyDto("apicurio.a", snapshot);
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<DynamicConfigPropertyDto> slowLoad = executor
                    .submit(() -> cache.getConfigProperty("apicurio.a"));
            assertTrue(loadStarted.await(10, TimeUnit.SECONDS), "load did not start");

            stored.set("new");
            cache.setConfigProperty(new DynamicConfigPropertyDto("apicurio.a", "new"));
            invalidated.countDown();

            // The in-flight read still returns what storage handed it, but it must not survive the
            // invalidation, or that stale value is served until a later refresh happens to notice.
            assertEquals("old", slowLoad.get(10, TimeUnit.SECONDS).getValue());
            assertEquals("new", cache.getConfigProperty("apicurio.a").getValue());
        } finally {
            executor.shutdownNow();
        }
    }
}
