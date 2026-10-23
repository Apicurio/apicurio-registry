package io.apicurio.registry.auth;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
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
    void eachRequestReadsStorageAgain() {
        RegistryStorage storage = storageWithArtifact();
        inRequest(() -> lookups.artifact(storage, "g", "a"));
        inRequest(() -> lookups.artifact(storage, "g", "a"));
        verify(storage, times(2)).getArtifactMetaData("g", "a");
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
