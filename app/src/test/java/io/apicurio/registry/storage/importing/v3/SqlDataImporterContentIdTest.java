package io.apicurio.registry.storage.importing.v3;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void versionWhoseContentWasNotImportedIsDroppedWithAWarning() {
        RegistryStorage storage = mock(RegistryStorage.class);
        Logger logger = mock(Logger.class);
        SqlDataImporter importer = new SqlDataImporter(logger, mock(RegistryStorageContentUtils.class), storage,
                false, false);

        ArtifactVersionEntity version = ArtifactVersionEntity.builder().groupId("billing").artifactId("invoice")
                .version("1.0").globalId(5L).contentId(99L).build();
        importer.importArtifactVersion(version);

        verify(logger).warn(eq("Dropped artifact version {}/{} version {} (globalId {}) from the import: its "
                + "content (contentId {}) was not imported"), eq("billing"), eq("invoice"), eq("1.0"), eq(5L),
                eq(99L));
        verify(storage, never()).importArtifactVersion(any());
    }

    @Test
    void commentWhoseVersionWasNotImportedIsDroppedWithAWarning() {
        RegistryStorage storage = mock(RegistryStorage.class);
        Logger logger = mock(Logger.class);
        SqlDataImporter importer = new SqlDataImporter(logger, mock(RegistryStorageContentUtils.class), storage,
                false, false);

        importer.importComment(CommentEntity.builder().commentId("c-1").globalId(5L).value("hi").build());

        verify(logger).warn(eq("Dropped comment {} from the import: its version (globalId {}) was not imported"),
                eq("c-1"), eq(5L));
        verify(storage, never()).importComment(any());
    }
}
