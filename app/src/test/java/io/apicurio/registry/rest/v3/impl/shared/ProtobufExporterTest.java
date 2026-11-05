package io.apicurio.registry.rest.v3.impl.shared;

import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.references.RecordingAuthorizingLoader;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Protobuf export authorizes the files imported by each file together, before loading any of them.
 */
class ProtobufExporterTest {

    private ProtobufExporter exporter(RecordingAuthorizingLoader loader) {
        RegistryStorage storage = mock(RegistryStorage.class);
        StoredArtifactVersionDto main = StoredArtifactVersionDto.builder()
                .content(ContentHandle.create("syntax = \"proto3\";\npackage main;\n"))
                .references(List.of(RecordingAuthorizingLoader.ref("a"), RecordingAuthorizingLoader.ref("b"),
                        RecordingAuthorizingLoader.ref("c")))
                .build();
        when(storage.getArtifactVersionContent("g", "main", "1")).thenReturn(main);
        ResourceAccessGuard guard = mock(ResourceAccessGuard.class);
        when(guard.referenceLoader()).thenReturn(loader);

        ProtobufExporter exporter = new ProtobufExporter();
        exporter.storage = storage;
        exporter.accessGuard = guard;
        exporter.log = mock(Logger.class);
        return exporter;
    }

    @Test
    void theImportsOfEachFileAreAuthorizedInOneCallBeforeAnyIsLoaded() {
        RecordingAuthorizingLoader loader = new RecordingAuthorizingLoader(Set.of()).with("a", "d", "e");

        exporter(loader).exportVersionAsZip("g", "main", "1");

        // Depth-first: main's imports, then a's imports when a is loaded
        assertEquals(List.of(List.of("a", "b", "c"), List.of("d", "e")), loader.authorizedBatches);
        assertEquals(List.of("a", "d", "e", "b", "c"), loader.loaded);
    }

    @Test
    void aDeniedImportFailsTheExportBeforeAnyFileIsLoaded() {
        RecordingAuthorizingLoader loader = new RecordingAuthorizingLoader(Set.of("c"));

        assertThrows(ForbiddenException.class, () -> exporter(loader).exportVersionAsZip("g", "main", "1"));
        assertEquals(List.of(), loader.loaded);
    }

    @Test
    void aFileImportedTwiceIsAuthorizedAndLoadedOnce() {
        RecordingAuthorizingLoader loader = new RecordingAuthorizingLoader(Set.of()).with("a", "d").with("b", "d");

        exporter(loader).exportVersionAsZip("g", "main", "1");

        assertEquals(List.of(List.of("a", "b", "c"), List.of("d")), loader.authorizedBatches);
        assertEquals(List.of("a", "d", "b", "c"), loader.loaded);
    }
}
