package io.apicurio.registry.storage.importing.v3;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlDataImporterContentIdTest {

    private static ContentEntity content(long contentId, String hash) {
        return ContentEntity.builder().contentId(contentId).contentHash(hash).canonicalHash("canonical")
                .contentType("application/json").contentBytes("{}".getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void existingContentIsReusedWhenContentIdIsNotPreserved() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.contentIdFromHash("existing-hash")).thenReturn(Optional.of(7L));
        SqlDataImporter importer = new SqlDataImporter(LoggerFactory.getLogger(getClass()),
                mock(RegistryStorageContentUtils.class), storage, false, false);

        importer.importContent(content(1L, "existing-hash"));

        assertEquals(7L, importer.contentIdMapping.get(1L));
        verify(storage, never()).importContent(any(ContentEntity.class));
        verify(storage, never()).nextContentId();
    }

    @Test
    void newContentGetsANewContentId() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.contentIdFromHash("new-hash")).thenReturn(Optional.empty());
        when(storage.nextContentId()).thenReturn(42L);
        SqlDataImporter importer = new SqlDataImporter(LoggerFactory.getLogger(getClass()),
                mock(RegistryStorageContentUtils.class), storage, false, false);

        importer.importContent(content(1L, "new-hash"));

        assertEquals(42L, importer.contentIdMapping.get(1L));
        verify(storage).importContent(any(ContentEntity.class));
    }
}
