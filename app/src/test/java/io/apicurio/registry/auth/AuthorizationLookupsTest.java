package io.apicurio.registry.auth;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
     * Unlike every other lookup in this class, {@link AuthorizationLookups#coordinates} is also
     * cached across requests (see {@link GlobalIdCoordinatesCache}): a global ID's coordinates
     * never change, so a second request for the same global ID must not read storage again.
     */
    @Test
    void coordinatesOfAResolvedGlobalIdAreRememberedAcrossRequests() {
        // A value unlikely to collide with any other test sharing this cache's singleton instance.
        long globalId = uniqueGlobalId();
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(globalId))
                .thenReturn(io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto.builder()
                        .groupId("g").artifactId("a").version("1").build());

        inRequest(() -> assertEquals("a",
                lookups.coordinates(storage, globalId).orElseThrow().artifactId()));
        inRequest(() -> assertEquals("a",
                lookups.coordinates(storage, globalId).orElseThrow().artifactId()));

        verify(storage, times(1)).getArtifactVersionMetaData(globalId);
    }

    /** A global ID that does not resolve is not remembered across requests, unlike a resolved one. */
    @Test
    void coordinatesOfAnUnresolvedGlobalIdAreCheckedEveryRequest() {
        long globalId = uniqueGlobalId();
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionMetaData(globalId))
                .thenThrow(new io.apicurio.registry.storage.error.VersionNotFoundException(globalId));

        inRequest(() -> assertEquals(Optional.empty(), lookups.coordinates(storage, globalId)));
        inRequest(() -> assertEquals(Optional.empty(), lookups.coordinates(storage, globalId)));

        verify(storage, times(2)).getArtifactVersionMetaData(globalId);
    }

    /**
     * {@link GlobalIdCoordinatesCache} is an application-wide singleton shared by every test in this
     * class (and, within one JVM, every other test touching the same bean), so each test that caches
     * a global ID must use one no other test can plausibly use, not a small fixed number like the
     * per-request tests above use safely (those caches are torn down between requests).
     */
    private static long uniqueGlobalId() {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
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
