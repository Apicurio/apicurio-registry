package io.apicurio.registry.auth;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GlobalIdCoordinatesCache} caches only a global ID's (group, artifact, version) coordinates,
 * never anything mutable, and never caches "not found" — see the class Javadoc for why.
 */
class GlobalIdCoordinatesCacheTest {

    private static GlobalIdCoordinatesCache newCache(boolean enabled) {
        GlobalIdCoordinatesCache cache = new GlobalIdCoordinatesCache();
        cache.enabled = enabled;
        cache.maxSize = 100;
        cache.meterRegistry = new SimpleMeterRegistry();
        cache.init();
        return cache;
    }

    private static ArtifactVersionMetaDataDto vmd(String groupId, String artifactId, String version) {
        return ArtifactVersionMetaDataDto.builder().groupId(groupId).artifactId(artifactId).version(version)
                .build();
    }

    @Test
    void aResolvedGlobalIdIsReadFromStorageOnlyOnce() {
        GlobalIdCoordinatesCache cache = newCache(true);
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(1L)).thenReturn(vmd("g", "a", "1"));

        for (int i = 0; i < 3; i++) {
            GlobalIdCoordinates resolved = cache.resolve(storage, 1L).orElseThrow();
            assertEquals("g", resolved.groupId());
            assertEquals("a", resolved.artifactId());
            assertEquals("1", resolved.version());
        }
        verify(storage, times(1)).getArtifactVersionMetaData(1L);
    }

    @Test
    void aGlobalIdThatDoesNotResolveIsCheckedEveryTime() {
        GlobalIdCoordinatesCache cache = newCache(true);
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(99L)).thenThrow(new VersionNotFoundException(99L));

        assertEquals(java.util.Optional.empty(), cache.resolve(storage, 99L));
        assertEquals(java.util.Optional.empty(), cache.resolve(storage, 99L));
        verify(storage, times(2)).getArtifactVersionMetaData(99L);
    }

    @Test
    void aGlobalIdThatStartsUnresolvedAndIsLaterAssignedResolvesOnceAssigned() {
        GlobalIdCoordinatesCache cache = newCache(true);
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(7L)).thenThrow(new VersionNotFoundException(7L))
                .thenReturn(vmd("g", "later", "1"));

        assertEquals(java.util.Optional.empty(), cache.resolve(storage, 7L));
        assertEquals("later", cache.resolve(storage, 7L).orElseThrow().artifactId());
        // Once resolved, it stays cached: a third call must not read storage again.
        assertEquals("later", cache.resolve(storage, 7L).orElseThrow().artifactId());
        verify(storage, times(2)).getArtifactVersionMetaData(7L);
    }

    @Test
    void disabledCacheReadsStorageEveryTime() {
        GlobalIdCoordinatesCache cache = newCache(false);
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(1L)).thenReturn(vmd("g", "a", "1"));

        cache.resolve(storage, 1L);
        cache.resolve(storage, 1L);
        verify(storage, times(2)).getArtifactVersionMetaData(1L);
    }

    @Test
    void onlyCoordinatesAreExposedNeverTheMutableMetadata() {
        // Compile-time guarantee, documented here: GlobalIdCoordinates has exactly three
        // components (group, artifact, version), so a caller can never read state, labels,
        // name, description, owner, or timestamps through this cache, however it is resolved.
        assertEquals(3, GlobalIdCoordinates.class.getRecordComponents().length,
                "GlobalIdCoordinates must only ever hold the three immutable coordinates");
    }

    @Test
    void invalidateAllForcesEveryEntryToBeReResolved() {
        GlobalIdCoordinatesCache cache = newCache(true);
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(1L)).thenReturn(vmd("g", "a", "1"));
        when(storage.getArtifactVersionMetaData(2L)).thenReturn(vmd("g", "b", "1"));

        assertEquals("a", cache.resolve(storage, 1L).orElseThrow().artifactId());
        assertEquals("b", cache.resolve(storage, 2L).orElseThrow().artifactId());
        verify(storage, times(1)).getArtifactVersionMetaData(1L);
        verify(storage, times(1)).getArtifactVersionMetaData(2L);

        cache.invalidateAll();

        // An import can rebind a global ID to different coordinates (preserveGlobalId reuse of a
        // deleted ID): after invalidateAll(), every previously-cached ID must be read from storage
        // again, not served from the stale entry.
        when(storage.getArtifactVersionMetaData(1L)).thenReturn(vmd("g", "rebound", "1"));
        assertEquals("rebound", cache.resolve(storage, 1L).orElseThrow().artifactId());
        verify(storage, times(2)).getArtifactVersionMetaData(1L);
        assertEquals("b", cache.resolve(storage, 2L).orElseThrow().artifactId());
        verify(storage, times(2)).getArtifactVersionMetaData(2L);
    }

    @Test
    void invalidateAllIsANoOpWhenDisabled() {
        GlobalIdCoordinatesCache cache = newCache(false);
        // Must not throw: the cache field is never initialized when disabled.
        cache.invalidateAll();
    }
}
