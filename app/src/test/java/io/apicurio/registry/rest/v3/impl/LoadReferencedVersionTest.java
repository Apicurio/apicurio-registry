package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The loader behind the reference-tree ETag maps a missing referenced version to {@code null}, so the walk
 * skips it, and lets every other storage failure propagate (#9626).
 */
class LoadReferencedVersionTest {

    private static final ArtifactReferenceDto REFERENCE = ArtifactReferenceDto.builder().groupId("g")
            .artifactId("money").version("1").name("money.json").build();

    private RegistryStorage storage;
    private IdsResourceImpl resource;

    @BeforeEach
    void setUp() {
        storage = mock(RegistryStorage.class);
        resource = new IdsResourceImpl();
        resource.storage = storage;
    }

    @Test
    void returnsTheReferencedVersion() {
        StoredArtifactVersionDto version = StoredArtifactVersionDto.builder().contentId(42L).build();
        when(storage.getArtifactVersionContent("g", "money", "1")).thenReturn(version);

        assertSame(version, resource.loadReferencedVersion(REFERENCE));
    }

    @Test
    void aMissingArtifactIsNull() {
        when(storage.getArtifactVersionContent("g", "money", "1"))
                .thenThrow(new ArtifactNotFoundException("g", "money"));

        assertNull(resource.loadReferencedVersion(REFERENCE));
    }

    @Test
    void aMissingVersionIsNull() {
        when(storage.getArtifactVersionContent("g", "money", "1"))
                .thenThrow(new VersionNotFoundException("g", "money", "1"));

        assertNull(resource.loadReferencedVersion(REFERENCE));
    }

    @Test
    void anyOtherStorageFailurePropagates() {
        RegistryStorageException failure = new RegistryStorageException("simulated storage failure");
        when(storage.getArtifactVersionContent("g", "money", "1")).thenThrow(failure);

        RegistryStorageException thrown = assertThrows(RegistryStorageException.class,
                () -> resource.loadReferencedVersion(REFERENCE));
        assertSame(failure, thrown);
    }
}
