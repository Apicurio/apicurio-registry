package io.apicurio.registry.auth;

import io.apicurio.registry.auth.grants.GrantsAccessController;
import io.apicurio.registry.references.AuthorizingReferenceLoader;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Batched per-resource authorization in {@link ResourceAccessGuard}: the same decisions as checking
 * each resource, with the grants of a whole batch evaluated in one call.
 */
class ResourceAccessGuardBatchTest {

    private final GrantsAccessController grantsAc = mock(GrantsAccessController.class);
    private final RegistryStorage storage = mock(RegistryStorage.class);
    private ResourceAccessGuard guard;

    private static final AuthorizedResource A = AuthorizedResource.artifact("g", "a");
    private static final AuthorizedResource B = AuthorizedResource.artifact("g", "b");

    @BeforeEach
    void setUp() {
        guard = spy(new ResourceAccessGuard());
        guard.grantsAc = grantsAc;
        guard.storage = storage;
        doReturn(true).when(guard).isEnforced(any());
        when(grantsAc.isAllowedForAll(any(), anyList())).thenReturn(true);
    }

    private static ArtifactReferenceDto ref(String artifactId) {
        return ArtifactReferenceDto.builder().groupId("g").artifactId(artifactId).version("1").build();
    }

    @Test
    void ownersAreLeftOutOfTheBatchAndDuplicatesAreEvaluatedOnce() {
        when(grantsAc.isStrictOwner(A)).thenReturn(true);

        guard.requireGrantsAccess(AuthorizedLevel.Read, List.of(A, B, B));

        verify(grantsAc).isAllowedForAll(AuthorizedLevel.Read, List.of(B));
        verify(grantsAc, never()).isAllowed(any(), any());
    }

    @Test
    void aDeniedBatchIsForbidden() {
        when(grantsAc.isAllowedForAll(AuthorizedLevel.Read, List.of(A, B))).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> guard.requireGrantsAccess(AuthorizedLevel.Read, List.of(A, B)));
    }

    @Test
    void nothingIsEvaluatedWhenGrantsDoNotApply() {
        doReturn(false).when(guard).isEnforced(any());

        guard.requireGrantsAccess(AuthorizedLevel.Read, List.of(A, B));

        verifyNoInteractions(grantsAc);
    }

    @Test
    void referencesAuthorizedInABatchAreNotAuthorizedAgainWhenLoaded() {
        Function<ArtifactReferenceDto, ContentWrapperDto> loader = guard.referenceLoader();
        assertTrue(loader instanceof AuthorizingReferenceLoader);
        ContentWrapperDto content = ContentWrapperDto.builder().build();
        when(storage.getContentByReference(any())).thenReturn(content);

        ((AuthorizingReferenceLoader) loader).authorize(List.of(ref("a"), ref("b")));
        assertSame(content, loader.apply(ref("a")));
        loader.apply(ref("b"));

        verify(grantsAc, times(1)).isAllowedForAll(AuthorizedLevel.Read, List.of(A, B));
        // A reference that was not part of a batch is still authorized, on its own
        loader.apply(ref("c"));
        verify(grantsAc).isAllowedForAll(AuthorizedLevel.Read, List.of(AuthorizedResource.artifact("g", "c")));
        verify(storage, times(3)).getContentByReference(any());
        // Exactly two evaluations: the batch, and c on its own
        verify(grantsAc, times(2)).isAllowedForAll(any(), anyList());
    }

    @Test
    void aDeniedReferenceIsNeverLoaded() {
        when(grantsAc.isAllowedForAll(any(), anyList())).thenReturn(false);
        Function<ArtifactReferenceDto, ContentWrapperDto> loader = guard.referenceLoader();

        assertThrows(ForbiddenException.class, () -> loader.apply(ref("a")));
        assertThrows(ForbiddenException.class,
                () -> ((AuthorizingReferenceLoader) loader).authorize(List.of(ref("a"), ref("b"))));
        verify(storage, never()).getContentByReference(any());
    }

    @Test
    void withoutGrantsTheLoaderJustLoads() {
        doReturn(false).when(guard).isEnforced(any());

        assertFalse(guard.referenceLoader() instanceof AuthorizingReferenceLoader);
    }
}
