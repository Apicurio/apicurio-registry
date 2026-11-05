package io.apicurio.registry.references;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.apicurio.registry.references.RecordingAuthorizingLoader.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthorizingReferenceLoader#preferring}: content planned by write hooks, but not yet persisted, is
 * served without being loaded, yet authorized exactly like any other reference.
 */
class AuthorizingReferenceLoaderTest {

    private static final ContentWrapperDto PLANNED = ContentWrapperDto.builder()
            .content(ContentHandle.create("planned")).build();

    @Test
    void plannedReferencesAreAuthorizedButNotLoaded() {
        RecordingAuthorizingLoader fallback = new RecordingAuthorizingLoader(Set.of());
        Function<ArtifactReferenceDto, ContentWrapperDto> loader = AuthorizingReferenceLoader
                .preferring(Map.of(ref("planned"), PLANNED), fallback);
        assertTrue(loader instanceof AuthorizingReferenceLoader);

        ((AuthorizingReferenceLoader) loader).authorize(List.of(ref("planned"), ref("stored")));
        assertSame(PLANNED, loader.apply(ref("planned")));
        loader.apply(ref("stored"));

        assertEquals(List.of(List.of("planned", "stored"), List.of("planned"), List.of("stored")),
                fallback.authorizedBatches);
        assertEquals(List.of("stored"), fallback.loaded);
    }

    @Test
    void anUnreadablePlannedReferenceIsStillDenied() {
        RecordingAuthorizingLoader fallback = new RecordingAuthorizingLoader(Set.of("planned"));
        Function<ArtifactReferenceDto, ContentWrapperDto> loader = AuthorizingReferenceLoader
                .preferring(Map.of(ref("planned"), PLANNED), fallback);

        assertThrows(ForbiddenException.class, () -> loader.apply(ref("planned")));
        assertThrows(ForbiddenException.class,
                () -> ((AuthorizingReferenceLoader) loader).authorize(List.of(ref("planned"))));
    }

    @Test
    void aPlainFallbackIsNotCalledForPlannedReferences() {
        List<String> loaded = new ArrayList<>();
        Function<ArtifactReferenceDto, ContentWrapperDto> fallback = reference -> {
            loaded.add(reference.getArtifactId());
            return null;
        };
        Function<ArtifactReferenceDto, ContentWrapperDto> loader = AuthorizingReferenceLoader
                .preferring(Map.of(ref("planned"), PLANNED), fallback);

        assertSame(PLANNED, loader.apply(ref("planned")));
        loader.apply(ref("stored"));

        assertEquals(List.of("stored"), loaded);
    }
}
