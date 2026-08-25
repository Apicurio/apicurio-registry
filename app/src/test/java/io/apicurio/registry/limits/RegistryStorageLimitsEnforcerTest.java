package io.apicurio.registry.limits;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class RegistryStorageLimitsEnforcerTest {

    @Mock
    private RegistryLimitsService limitsService;

    @Mock
    private RegistryStorage delegate;

    @InjectMocks
    private RegistryStorageLimitsEnforcer enforcer;

    @BeforeEach
    public void setUp() {
        enforcer.setDelegate(delegate);
    }

    @Test
    public void testCreateArtifactDryRunTrueDoesNotIncrementCounter() {
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto();
        ContentWrapperDto content = new ContentWrapperDto();
        EditableVersionMetaDataDto versionMetaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifact(metaData, content, versionMetaData))
                .thenReturn(LimitsCheckResult.ok());
        Mockito.when(delegate.createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                Collections.emptyList(), false, true, "owner"))
                .thenReturn(Pair.of(new ArtifactMetaDataDto(), new ArtifactVersionMetaDataDto()));

        enforcer.createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                Collections.emptyList(), false, true, "owner");

        InOrder inOrder = Mockito.inOrder(limitsService, delegate);
        inOrder.verify(limitsService).canCreateArtifact(metaData, content, versionMetaData);
        inOrder.verify(delegate).createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                Collections.emptyList(), false, true, "owner");
        Mockito.verify(limitsService, Mockito.never()).artifactCreated();
    }

    @Test
    public void testCreateArtifactDryRunFalseIncrementsCounter() {
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto();
        ContentWrapperDto content = new ContentWrapperDto();
        EditableVersionMetaDataDto versionMetaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifact(metaData, content, versionMetaData))
                .thenReturn(LimitsCheckResult.ok());
        Mockito.when(delegate.createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                Collections.emptyList(), false, false, "owner"))
                .thenReturn(Pair.of(new ArtifactMetaDataDto(), new ArtifactVersionMetaDataDto()));

        enforcer.createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                Collections.emptyList(), false, false, "owner");

        InOrder inOrder = Mockito.inOrder(limitsService, delegate);
        inOrder.verify(limitsService).canCreateArtifact(metaData, content, versionMetaData);
        inOrder.verify(delegate).createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                Collections.emptyList(), false, false, "owner");
        inOrder.verify(limitsService).artifactCreated();
    }

    @Test
    public void testCreateArtifactLimitExceededThrowsExceptionAndDoesNotInvokeDelegate() {
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto();
        ContentWrapperDto content = new ContentWrapperDto();
        EditableVersionMetaDataDto versionMetaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifact(metaData, content, versionMetaData))
                .thenReturn(LimitsCheckResult.disallowed("Artifact limit reached"));

        LimitExceededException ex = assertThrows(
                LimitExceededException.class,
                () -> enforcer.createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                        Collections.emptyList(), false, false, "owner")
        );

        assertEquals("Artifact limit reached", ex.getMessage());
        Mockito.verify(limitsService).canCreateArtifact(metaData, content, versionMetaData);
        Mockito.verify(delegate, Mockito.never()).createArtifact(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyList(), Mockito.anyBoolean(),
                Mockito.anyBoolean(), Mockito.anyString());
        Mockito.verify(limitsService, Mockito.never()).artifactCreated();
    }

    @Test
    public void testCreateArtifactLimitExceededWithDryRunTrueStillThrowsException() {
        EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto();
        ContentWrapperDto content = new ContentWrapperDto();
        EditableVersionMetaDataDto versionMetaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifact(metaData, content, versionMetaData))
                .thenReturn(LimitsCheckResult.disallowed("Artifact limit reached"));

        LimitExceededException ex = assertThrows(
                LimitExceededException.class,
                () -> enforcer.createArtifact("g1", "a1", "JSON", metaData, "1", content, versionMetaData,
                        Collections.emptyList(), false, true, "owner")
        );

        assertEquals("Artifact limit reached", ex.getMessage());
        Mockito.verify(limitsService).canCreateArtifact(metaData, content, versionMetaData);
        Mockito.verify(delegate, Mockito.never()).createArtifact(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyList(), Mockito.anyBoolean(),
                Mockito.anyBoolean(), Mockito.anyString());
        Mockito.verify(limitsService, Mockito.never()).artifactCreated();
    }

    @Test
    public void testCreateArtifactVersionDryRunTrueDoesNotIncrementCounter() {
        ContentHandle contentHandle = ContentHandle.create("content");
        ContentWrapperDto content = new ContentWrapperDto();
        content.setContent(contentHandle);
        EditableVersionMetaDataDto metaData = new EditableVersionMetaDataDto();

        // Note: 3rd parameter is null because previousVersion check is optional for version creation limit checks
        Mockito.when(limitsService.canCreateArtifactVersion("g1", "a1", null, contentHandle))
                .thenReturn(LimitsCheckResult.ok());
        Mockito.when(delegate.createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                Collections.emptyList(), false, true, "owner"))
                .thenReturn(new ArtifactVersionMetaDataDto());

        enforcer.createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                Collections.emptyList(), false, true, "owner");

        InOrder inOrder = Mockito.inOrder(limitsService, delegate);
        inOrder.verify(limitsService).canCreateArtifactVersion("g1", "a1", null, contentHandle);
        inOrder.verify(delegate).createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                Collections.emptyList(), false, true, "owner");
        Mockito.verify(limitsService, Mockito.never()).artifactVersionCreated("g1", "a1");
    }

    @Test
    public void testCreateArtifactVersionDryRunFalseIncrementsCounter() {
        ContentHandle contentHandle = ContentHandle.create("content");
        ContentWrapperDto content = new ContentWrapperDto();
        content.setContent(contentHandle);
        EditableVersionMetaDataDto metaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifactVersion("g1", "a1", null, contentHandle))
                .thenReturn(LimitsCheckResult.ok());
        Mockito.when(delegate.createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                Collections.emptyList(), false, false, "owner"))
                .thenReturn(new ArtifactVersionMetaDataDto());

        enforcer.createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                Collections.emptyList(), false, false, "owner");

        InOrder inOrder = Mockito.inOrder(limitsService, delegate);
        inOrder.verify(limitsService).canCreateArtifactVersion("g1", "a1", null, contentHandle);
        inOrder.verify(delegate).createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                Collections.emptyList(), false, false, "owner");
        inOrder.verify(limitsService).artifactVersionCreated("g1", "a1");
    }

    @Test
    public void testCreateArtifactVersionLimitExceededThrowsExceptionAndDoesNotInvokeDelegate() {
        ContentHandle contentHandle = ContentHandle.create("content");
        ContentWrapperDto content = new ContentWrapperDto();
        content.setContent(contentHandle);
        EditableVersionMetaDataDto metaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifactVersion("g1", "a1", null, contentHandle))
                .thenReturn(LimitsCheckResult.disallowed("Artifact version limit reached"));

        LimitExceededException ex = assertThrows(
                LimitExceededException.class,
                () -> enforcer.createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                        Collections.emptyList(), false, false, "owner")
        );

        assertEquals("Artifact version limit reached", ex.getMessage());
        Mockito.verify(limitsService).canCreateArtifactVersion("g1", "a1", null, contentHandle);
        Mockito.verify(delegate, Mockito.never()).createArtifactVersion(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyList(), Mockito.anyBoolean(),
                Mockito.anyBoolean(), Mockito.anyString());
        Mockito.verify(limitsService, Mockito.never()).artifactVersionCreated("g1", "a1");
    }

    @Test
    public void testCreateArtifactVersionLimitExceededWithDryRunTrueStillThrowsException() {
        ContentHandle contentHandle = ContentHandle.create("content");
        ContentWrapperDto content = new ContentWrapperDto();
        content.setContent(contentHandle);
        EditableVersionMetaDataDto metaData = new EditableVersionMetaDataDto();

        Mockito.when(limitsService.canCreateArtifactVersion("g1", "a1", null, contentHandle))
                .thenReturn(LimitsCheckResult.disallowed("Artifact version limit reached"));

        LimitExceededException ex = assertThrows(
                LimitExceededException.class,
                () -> enforcer.createArtifactVersion("g1", "a1", "1", "JSON", content, metaData,
                        Collections.emptyList(), false, true, "owner")
        );

        assertEquals("Artifact version limit reached", ex.getMessage());
        Mockito.verify(limitsService).canCreateArtifactVersion("g1", "a1", null, contentHandle);
        Mockito.verify(delegate, Mockito.never()).createArtifactVersion(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyList(), Mockito.anyBoolean(),
                Mockito.anyBoolean(), Mockito.anyString());
        Mockito.verify(limitsService, Mockito.never()).artifactVersionCreated("g1", "a1");
    }
}
