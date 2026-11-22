package io.apicurio.registry.auth;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.VersionWithArtifactMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization lookups are remembered for one request only, including lookups of missing
 * resources, and are not remembered outside a request.
 */
@QuarkusTest
public class AuthorizationLookupsTest {

    @Inject
    AuthorizationLookups lookups;

    private static RegistryStorage storageWithArtifact() {
        RegistryStorage storage = mock(RegistryStorage.class);
        doReturn(ArtifactMetaDataDto.builder().groupId("g").artifactId("a").owner("alice").build())
                .when(storage).getArtifactMetaData("g", "a");
        when(storage.getArtifactMetaData("g", "missing")).thenThrow(new ArtifactNotFoundException("missing"));
        when(storage.getGroupMetaData("missing")).thenThrow(new GroupNotFoundException("missing"));
        return storage;
    }

    /** Runs the action in a new request context, on a new thread so it is not the test's own. */
    private static void inRequest(Runnable action) {
        CompletableFuture.runAsync(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                action.run();
            } finally {
                requestContext.terminate();
            }
        }, runnable -> new Thread(runnable).start()).orTimeout(30, TimeUnit.SECONDS).join();
    }

    @Test
    void lookupsAreRememberedForTheRestOfTheRequest() {
        RegistryStorage storage = storageWithArtifact();
        inRequest(() -> {
            for (int i = 0; i < 3; i++) {
                assertEquals("alice", lookups.artifact(storage, "g", "a").map(ArtifactMetaDataDto::getOwner)
                        .orElseThrow());
                assertEquals(Optional.empty(), lookups.artifact(storage, "g", "missing"));
                assertEquals(Optional.empty(), lookups.group(storage, "missing"));
            }
        });
        verify(storage, times(1)).getArtifactMetaData("g", "a");
        verify(storage, times(1)).getArtifactMetaData("g", "missing");
        verify(storage, times(1)).getGroupMetaData("missing");
    }

    @Test
    void aMissingResourceStaysMissingForTheRestOfTheRequest() {
        RegistryStorage storage = mock(RegistryStorage.class);
        // The resource does not exist at the first lookup, and is created later in the request
        when(storage.getArtifactMetaData("g", "created")).thenThrow(new ArtifactNotFoundException("created"))
                .thenReturn(ArtifactMetaDataDto.builder().groupId("g").artifactId("created").owner("bob").build());
        when(storage.getGroupMetaData("new-group")).thenThrow(new GroupNotFoundException("new-group"))
                .thenReturn(GroupMetaDataDto.builder().groupId("new-group").owner("bob").build());
        inRequest(() -> {
            assertEquals(Optional.empty(), lookups.artifact(storage, "g", "created"));
            assertEquals(Optional.empty(), lookups.group(storage, "new-group"));
            // Still missing: the lookups are not refreshed after a write in the same request
            assertEquals(Optional.empty(), lookups.artifact(storage, "g", "created"));
            assertEquals(Optional.empty(), lookups.group(storage, "new-group"));
        });
        // The next request sees the created resources
        inRequest(() -> {
            assertEquals("bob", lookups.artifact(storage, "g", "created").map(ArtifactMetaDataDto::getOwner)
                    .orElseThrow());
            assertEquals("bob", lookups.group(storage, "new-group").map(GroupMetaDataDto::getOwner)
                    .orElseThrow());
        });
    }

    @Test
    void eachRequestReadsStorageAgain() {
        RegistryStorage storage = storageWithArtifact();
        inRequest(() -> lookups.artifact(storage, "g", "a"));
        inRequest(() -> lookups.artifact(storage, "g", "a"));
        verify(storage, times(2)).getArtifactMetaData("g", "a");
    }

    /**
     * Regression test: a data import preserving global IDs can rebind a global ID to a different
     * artifact. Authorization must resolve the global ID again in every request, so the second
     * request is authorized against the artifact the ID names now, not the one it named before.
     */
    @Test
    void coordinatesAreResolvedAgainInEveryRequestSoARebindIsSeen() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionWithArtifactMetaData(42L))
                .thenReturn(versionOf("team-a", "before"))
                .thenReturn(versionOf("team-b", "after"));

        inRequest(() -> {
            assertEquals(new GlobalIdCoordinates("team-a", "before", "1"),
                    lookups.coordinates(storage, 42L).orElseThrow());
            // Remembered for the rest of this request only
            assertEquals(new GlobalIdCoordinates("team-a", "before", "1"),
                    lookups.coordinates(storage, 42L).orElseThrow());
        });
        inRequest(() -> assertEquals(new GlobalIdCoordinates("team-b", "after", "1"),
                lookups.coordinates(storage, 42L).orElseThrow()));

        verify(storage, times(2)).getArtifactVersionWithArtifactMetaData(42L);
    }

    /** A global ID that does not resolve resolves as missing, and is checked again by the next request. */
    @Test
    void anUnresolvedGlobalIdIsCheckedAgainInTheNextRequest() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionWithArtifactMetaData(7L)).thenThrow(new VersionNotFoundException(7L));

        inRequest(() -> assertEquals(Optional.empty(), lookups.coordinates(storage, 7L)));
        inRequest(() -> assertEquals(Optional.empty(), lookups.coordinates(storage, 7L)));

        verify(storage, times(2)).getArtifactVersionWithArtifactMetaData(7L);
    }

    private static VersionWithArtifactMetaDataDto versionOf(String groupId, String artifactId) {
        return new VersionWithArtifactMetaDataDto(
                ArtifactVersionMetaDataDto.builder().groupId(groupId).artifactId(artifactId).version("1").build(),
                ArtifactMetaDataDto.builder().groupId(groupId).artifactId(artifactId).owner("owner-of-" + artifactId)
                        .build());
    }

    /**
     * Resolving a global ID reads its version and artifact together, once: the coordinates, the
     * version (which the endpoint reuses) and the artifact's owner all come from that read.
     */
    @Test
    void aGlobalIdIsResolvedWithItsArtifactInOneRead() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionWithArtifactMetaData(42L)).thenReturn(versionOf("team-a", "orders"));
        inRequest(() -> {
            assertEquals(new GlobalIdCoordinates("team-a", "orders", "1"),
                    lookups.coordinates(storage, 42L).orElseThrow());
            assertEquals("orders", lookups.version(storage, 42L).orElseThrow().getArtifactId());
            assertEquals("owner-of-orders", lookups.artifact(storage, "team-a", "orders")
                    .map(ArtifactMetaDataDto::getOwner).orElseThrow());
        });
        verify(storage, times(1)).getArtifactVersionWithArtifactMetaData(42L);
        verify(storage, never()).getArtifactVersionMetaData(42L);
        verify(storage, never()).getArtifactMetaData("team-a", "orders");
    }

    /**
     * Prefetched artifacts, found or missing, need no read each. An artifact the database matched
     * under another spelling is left to its own lookup, which reads it as storage matches it.
     */
    @Test
    void prefetchedArtifactsAreReadTogether() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactsMetaData(anyCollection())).thenReturn(List.of(
                ArtifactMetaDataDto.builder().groupId("g").artifactId("a").owner("alice").build(),
                ArtifactMetaDataDto.builder().groupId("g").artifactId("stored-case").owner("bob").build()));
        doReturn(ArtifactMetaDataDto.builder().groupId("g").artifactId("stored-case").owner("bob").build())
                .when(storage).getArtifactMetaData("g", "STORED-CASE");
        inRequest(() -> {
            lookups.prefetchArtifacts(storage, List.of(AuthorizedResource.artifact("g", "a"),
                    AuthorizedResource.artifact("g", "missing"), AuthorizedResource.artifact("g", "STORED-CASE"),
                    AuthorizedResource.group("g")));
            assertEquals("alice", lookups.artifact(storage, "g", "a").map(ArtifactMetaDataDto::getOwner)
                    .orElseThrow());
            assertEquals(Optional.empty(), lookups.artifact(storage, "g", "missing"));
            assertEquals("bob", lookups.artifact(storage, "g", "STORED-CASE").map(ArtifactMetaDataDto::getOwner)
                    .orElseThrow());
        });
        verify(storage, times(1)).getArtifactsMetaData(anyCollection());
        verify(storage, never()).getArtifactMetaData("g", "a");
        verify(storage, never()).getArtifactMetaData("g", "missing");
        verify(storage, times(1)).getArtifactMetaData("g", "STORED-CASE");
    }

    @Test
    void prefetchSkipsLookupsAlreadyMadeAndSingleArtifacts() {
        RegistryStorage storage = storageWithArtifact();
        inRequest(() -> {
            lookups.artifact(storage, "g", "a");
            // Only one artifact not looked up yet: its own lookup costs the same single read
            lookups.prefetchArtifacts(storage, List.of(AuthorizedResource.artifact("g", "a"),
                    AuthorizedResource.artifact("g", "missing")));
        });
        verify(storage, never()).getArtifactsMetaData(anyCollection());
    }

    @Test
    void lookupsOutsideARequestAreNotRemembered() throws Exception {
        RegistryStorage storage = storageWithArtifact();
        // Test methods run in a request context; a new thread has none, like a background job
        CompletableFuture<Boolean> contextActive = CompletableFuture.supplyAsync(() -> {
            boolean active = Arc.container().requestContext().isActive();
            lookups.artifact(storage, "g", "a");
            lookups.artifact(storage, "g", "a");
            return active;
        }, runnable -> new Thread(runnable).start());
        assertFalse(contextActive.get(30, TimeUnit.SECONDS));
        verify(storage, times(2)).getArtifactMetaData("g", "a");
    }

    @Test
    void theDefaultGroupIsOneEntryWhateverItIsCalled() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getGroupMetaData(null)).thenThrow(new GroupNotFoundException("default"));
        when(storage.getGroupMetaData("default")).thenThrow(new GroupNotFoundException("default"));
        inRequest(() -> {
            lookups.group(storage, null);
            lookups.group(storage, "default");
        });
        verify(storage, times(1)).getGroupMetaData(null);
        verify(storage, times(0)).getGroupMetaData("default");
    }
}
