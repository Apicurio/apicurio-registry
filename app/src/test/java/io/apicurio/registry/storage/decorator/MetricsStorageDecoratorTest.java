package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricsStorageDecoratorTest {

    private RegistryStorage delegate;
    private OTelMetricsProvider metrics;
    private MetricsStorageDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(RegistryStorage.class);
        metrics = mock(OTelMetricsProvider.class);
        decorator = new MetricsStorageDecorator();
        decorator.metrics = metrics;
        decorator.setDelegate(delegate);
    }

    private static ArtifactMetaDataDto artifact(String type) {
        ArtifactMetaDataDto dto = new ArtifactMetaDataDto();
        dto.setArtifactType(type);
        return dto;
    }

    private static ArtifactVersionMetaDataDto version(String type) {
        ArtifactVersionMetaDataDto dto = new ArtifactVersionMetaDataDto();
        dto.setArtifactType(type);
        return dto;
    }

    private void stubCreateArtifact(ArtifactVersionMetaDataDto firstVersion) {
        when(delegate.createArtifact(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any())).thenReturn(Pair.of(artifact("AVRO"), firstVersion));
    }

    @Test
    void createArtifactWithAFirstVersionCountsBoth() {
        stubCreateArtifact(version("AVRO"));
        decorator.createArtifact("orders", "a", null, null, null, null, null, List.of(), false, false, "me");
        verify(metrics).recordArtifactCreated("orders", "AVRO");
        verify(metrics).recordVersionCreated("orders", "AVRO");
    }

    @Test
    void createArtifactWithoutAVersionCountsOnlyTheArtifact() {
        stubCreateArtifact(null);
        decorator.createArtifact(null, "a", "AVRO", null, null, null, null, List.of(), false, false, "me");
        verify(metrics).recordArtifactCreated(null, "AVRO");
        verify(metrics, never()).recordVersionCreated(any(), any());
    }

    @Test
    void dryRunsAreNotCounted() {
        stubCreateArtifact(version("AVRO"));
        when(delegate.createArtifactVersion(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any())).thenReturn(version("AVRO"));
        decorator.createArtifact("g", "a", null, null, null, null, null, List.of(), false, true, "me");
        decorator.createArtifactVersion("g", "a", null, null, null, null, List.of(), false, true, "me");
        verifyNoInteractions(metrics);
    }

    @Test
    void createVersionCountsTheStoredType() {
        when(delegate.createArtifactVersion(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any())).thenReturn(version("PROTOBUF"));
        decorator.createArtifactVersion("g", "a", null, null, null, null, List.of(), false, false, "me");
        verify(metrics).recordVersionCreated("g", "PROTOBUF");
    }

    @Test
    void createVersionIfLatestIsCounted() {
        when(delegate.createArtifactVersionIfLatest(any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), anyInt(), any())).thenReturn(version("JSON"));
        decorator.createArtifactVersionIfLatest("g", "a", null, null, null, null, List.of(), false, "me", 1, null);
        verify(metrics).recordVersionCreated("g", "JSON");
    }

    @Test
    void deleteArtifactCountsTheTypeReadBeforeDeletion() {
        when(delegate.getArtifactMetaData("g", "a")).thenReturn(artifact("AVRO"));
        when(delegate.deleteArtifact("g", "a")).thenReturn(List.of("1"));
        decorator.deleteArtifact("g", "a");
        var order = inOrder(delegate, metrics);
        order.verify(delegate).getArtifactMetaData("g", "a");
        order.verify(delegate).deleteArtifact("g", "a");
        order.verify(metrics).recordArtifactDeleted("g", "AVRO");
    }

    @Test
    void aFailedTypeLookupDoesNotFailTheDelete() {
        // A KafkaSQL replica may not have consumed the creation yet; the delete still goes ahead
        when(delegate.getArtifactMetaData("g", "a")).thenThrow(new ArtifactNotFoundException("g", "a"));
        when(delegate.deleteArtifact("g", "a")).thenReturn(List.of("1"));
        assertEquals(List.of("1"), decorator.deleteArtifact("g", "a"));
        verify(metrics).recordArtifactDeleted("g", null);
    }

    @Test
    void failedWritesAreNotCounted() {
        when(delegate.getArtifactMetaData(eq("g"), eq("missing"))).thenThrow(new ArtifactNotFoundException("g", "missing"));
        when(delegate.deleteArtifact(eq("g"), eq("missing"))).thenThrow(new ArtifactNotFoundException("g", "missing"));
        assertThrows(ArtifactNotFoundException.class, () -> decorator.deleteArtifact("g", "missing"));
        verifyNoInteractions(metrics);
    }
}
