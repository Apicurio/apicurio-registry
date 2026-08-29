package io.apicurio.registry.config;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryStorageConfigCacheTest {

    private static final String PROPERTY = "apicurio.test.property";

    private RegistryStorage delegate;
    private RegistryStorageConfigCache cache;

    @BeforeEach
    void setUp() {
        delegate = mock(RegistryStorage.class);
        cache = new RegistryStorageConfigCache();
        cache.setDelegate(delegate);
    }

    @Test
    void testValueIsCachedAfterTheFirstRead() {
        when(delegate.getConfigProperty(PROPERTY)).thenReturn(dto("one"));

        assertEquals("one", cache.getConfigProperty(PROPERTY).getValue());
        assertEquals("one", cache.getConfigProperty(PROPERTY).getValue());

        verify(delegate, times(1)).getConfigProperty(PROPERTY);
    }

    @Test
    void testMissingValueIsNegativelyCached() {
        when(delegate.getConfigProperty(PROPERTY)).thenReturn(null);

        assertNull(cache.getConfigProperty(PROPERTY));
        assertNull(cache.getConfigProperty(PROPERTY));

        verify(delegate, times(1)).getConfigProperty(PROPERTY);
    }

    /**
     * A read that is already in flight when the cache is invalidated returns the value it loaded,
     * but must not leave that value behind in the cache. The load also has to stay outside the
     * map, or the invalidating thread blocks on the bin until storage answers.
     */
    @Test
    void testValueLoadedAcrossAnInvalidationIsNotCached() throws Exception {
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch invalidated = new CountDownLatch(1);
        AtomicReference<String> stored = new AtomicReference<>("old");

        when(delegate.getConfigProperty(PROPERTY)).thenAnswer(invocation -> {
            String snapshot = stored.get();
            loadStarted.countDown();
            assertTrue(invalidated.await(10, TimeUnit.SECONDS), "invalidation did not happen");
            return dto(snapshot);
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<DynamicConfigPropertyDto> slowLoad = executor
                    .submit(() -> cache.getConfigProperty(PROPERTY));
            assertTrue(loadStarted.await(10, TimeUnit.SECONDS), "load did not start");

            stored.set("new");
            cache.setConfigProperty(dto("new"));
            invalidated.countDown();

            assertEquals("old", slowLoad.get(10, TimeUnit.SECONDS).getValue());
            assertEquals("new", cache.getConfigProperty(PROPERTY).getValue());
        } finally {
            executor.shutdownNow();
        }
    }

    private static DynamicConfigPropertyDto dto(String value) {
        return new DynamicConfigPropertyDto(PROPERTY, value);
    }
}
