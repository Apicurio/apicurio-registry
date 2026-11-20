package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContentCacheDecoratorTest {

    private RegistryStorage delegate;
    private ContentCacheDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(RegistryStorage.class);

        decorator = new ContentCacheDecorator();
        decorator.enabled = true;
        decorator.maxWeightBytes = 1_000_000L;
        decorator.hotPathTtlSeconds = 30L;
        decorator.setDelegate(delegate);
        decorator.init();
    }

    private static ContentWrapperDto contentWrapper(String content) {
        return ContentWrapperDto.builder().contentType("application/json")
                .content(ContentHandle.create(content)).references(List.of()).artifactType("JSON").build();
    }

    @Test
    void getContentByIdCachesAcrossRepeatedCalls() {
        ContentWrapperDto content = contentWrapper("{\"a\":1}");
        when(delegate.getContentById(42L)).thenReturn(content);

        ContentWrapperDto first = decorator.getContentById(42L);
        ContentWrapperDto second = decorator.getContentById(42L);

        assertSame(content, first);
        assertSame(content, second);
        verify(delegate, times(1)).getContentById(42L);
    }

    @Test
    void getContentByIdDoesNotCacheContentNotFound() {
        when(delegate.getContentById(99L)).thenThrow(new ContentNotFoundException("not found"));

        assertThrows(ContentNotFoundException.class, () -> decorator.getContentById(99L));
        assertThrows(ContentNotFoundException.class, () -> decorator.getContentById(99L));

        verify(delegate, times(2)).getContentById(99L);
    }

    @Test
    void getContentByIdCoalescesConcurrentMisses() throws InterruptedException {
        ContentWrapperDto content = contentWrapper("{\"a\":1}");
        AtomicInteger invocationCount = new AtomicInteger();
        when(delegate.getContentById(7L)).thenAnswer(invocation -> {
            invocationCount.incrementAndGet();
            Thread.sleep(50);
            return content;
        });

        int threadCount = 8;
        Thread[] threads = new Thread[threadCount];
        ContentWrapperDto[] results = new ContentWrapperDto[threadCount];
        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            threads[i] = new Thread(() -> results[idx] = decorator.getContentById(7L));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertEquals(1, invocationCount.get());
        for (ContentWrapperDto result : results) {
            assertSame(content, result);
        }
    }

    @Test
    void getContentByHashCachesAcrossRepeatedCalls() {
        ContentWrapperDto content = contentWrapper("{\"b\":2}");
        when(delegate.getContentByHash("hash-1")).thenReturn(content);

        ContentWrapperDto first = decorator.getContentByHash("hash-1");
        ContentWrapperDto second = decorator.getContentByHash("hash-1");

        assertSame(content, first);
        assertSame(content, second);
        verify(delegate, times(1)).getContentByHash("hash-1");
    }

    @Test
    void disabledDecoratorDelegatesDirectlyWithoutCaching() {
        decorator = new ContentCacheDecorator();
        decorator.enabled = false;
        decorator.setDelegate(delegate);
        decorator.init();

        ContentWrapperDto content = contentWrapper("{\"a\":1}");
        when(delegate.getContentById(1L)).thenReturn(content);

        decorator.getContentById(1L);
        decorator.getContentById(1L);

        assertEquals(false, decorator.isEnabled());
    }

    @Test
    void getContentAndArtifactTypeByIdCachesAcrossRepeatedCalls() {
        ContentWrapperDto content = contentWrapper("{\"a\":1}");
        when(delegate.getContentAndArtifactTypeById(42L)).thenReturn(content);

        ContentWrapperDto first = decorator.getContentAndArtifactTypeById(42L);
        ContentWrapperDto second = decorator.getContentAndArtifactTypeById(42L);

        assertSame(content, first);
        assertSame(content, second);
        verify(delegate, times(1)).getContentAndArtifactTypeById(42L);
    }

    @Test
    void getContentAndArtifactTypeByIdDoesNotCacheContentNotFound() {
        when(delegate.getContentAndArtifactTypeById(99L)).thenThrow(new ContentNotFoundException("orphaned"));

        assertThrows(ContentNotFoundException.class, () -> decorator.getContentAndArtifactTypeById(99L));
        assertThrows(ContentNotFoundException.class, () -> decorator.getContentAndArtifactTypeById(99L));

        verify(delegate, times(2)).getContentAndArtifactTypeById(99L);
    }

    @Test
    void getContentAndArtifactTypeByIdIsNotCachedWhenHotPathTtlIsZero() {
        decorator = new ContentCacheDecorator();
        decorator.enabled = true;
        decorator.maxWeightBytes = 1_000_000L;
        decorator.hotPathTtlSeconds = 0L;
        decorator.setDelegate(delegate);
        decorator.init();

        ContentWrapperDto content = contentWrapper("{\"a\":1}");
        when(delegate.getContentAndArtifactTypeById(42L)).thenReturn(content);

        decorator.getContentAndArtifactTypeById(42L);
        decorator.getContentAndArtifactTypeById(42L);

        verify(delegate, times(2)).getContentAndArtifactTypeById(42L);
    }

    @Test
    void deleteArtifactVersionInvalidatesContentAndTypeCacheEntry() {
        ContentWrapperDto content = contentWrapper("{\"a\":1}");
        when(delegate.getContentAndArtifactTypeById(42L)).thenReturn(content);
        ArtifactVersionMetaDataDto metaData = new ArtifactVersionMetaDataDto();
        metaData.setContentId(42L);
        when(delegate.getArtifactVersionMetaData("g", "a", "1")).thenReturn(metaData);

        // Warm the cache.
        decorator.getContentAndArtifactTypeById(42L);
        verify(delegate, times(1)).getContentAndArtifactTypeById(42L);

        decorator.deleteArtifactVersion("g", "a", "1");
        verify(delegate, times(1)).deleteArtifactVersion("g", "a", "1");

        // Cache entry for content ID 42 must have been invalidated by the delete, so the next read is a
        // real miss again (simulating the content having become orphaned).
        when(delegate.getContentAndArtifactTypeById(42L)).thenThrow(new ContentNotFoundException("orphaned"));
        assertThrows(ContentNotFoundException.class, () -> decorator.getContentAndArtifactTypeById(42L));
        verify(delegate, times(2)).getContentAndArtifactTypeById(42L);
    }

    @Test
    void deleteArtifactVersionStillDeletesWhenMetaDataLookupFails() {
        when(delegate.getArtifactVersionMetaData("g", "a", "1"))
                .thenThrow(new ContentNotFoundException("gone"));

        decorator.deleteArtifactVersion("g", "a", "1");

        verify(delegate, times(1)).deleteArtifactVersion("g", "a", "1");
    }
}
