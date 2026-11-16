package io.apicurio.registry.storage.importing.v2;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.utils.impexp.v2.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v2.ContentEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlDataUpgraderContentIdTest {

    @Test
    void existingContentIsReusedWhenContentIdIsNotPreserved() {
        RegistryStorage storage = mock(RegistryStorage.class);
        RegistryStorageContentUtils utils = mock(RegistryStorageContentUtils.class);
        when(utils.getContentHash(any(), anyList())).thenReturn("existing-hash");
        when(storage.contentIdFromHash("existing-hash")).thenReturn(Optional.of(7L));
        SqlDataUpgrader upgrader = new SqlDataUpgrader(LoggerFactory.getLogger(getClass()), utils, storage,
                false, false);

        ContentEntity entity = new ContentEntity();
        entity.contentId = 1L;
        entity.contentBytes = "{}".getBytes(StandardCharsets.UTF_8);
        upgrader.importContent(entity);

        assertEquals(7L, upgrader.contentIdMapping.get(1L));
        verify(storage, never()).importContent(any());
        verify(storage, never()).nextContentId();
    }

    @Test
    void versionWhoseContentWasNotImportedIsDroppedWithAWarning() {
        RegistryStorage storage = mock(RegistryStorage.class);
        Logger logger = mock(Logger.class);
        SqlDataUpgrader upgrader = new SqlDataUpgrader(logger, mock(RegistryStorageContentUtils.class), storage,
                false, false);

        ArtifactVersionEntity version = new ArtifactVersionEntity();
        version.groupId = "billing";
        version.artifactId = "invoice";
        version.version = "1.0";
        version.globalId = 5L;
        version.contentId = 99L;
        version.state = ArtifactState.ENABLED;
        upgrader.importArtifactVersion(version);

        verify(logger).warn(eq("Dropped artifact version {}/{} version {} (globalId {}) from the import: its "
                + "content (contentId {}) was not imported"), eq("billing"), eq("invoice"), eq("1.0"), eq(5L),
                eq(99L));
        verify(storage, never()).importArtifactVersion(any());
    }
}
