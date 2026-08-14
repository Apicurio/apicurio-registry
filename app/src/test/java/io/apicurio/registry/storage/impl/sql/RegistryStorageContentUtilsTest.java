package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for GH #9590: a failing {@link ContentCanonicalizer} must not be silently
 * swallowed with the raw, non-canonicalized content returned in its place.
 */
public class RegistryStorageContentUtilsTest {

    private RegistryStorageContentUtils newUtils(ArtifactTypeUtilProviderFactory factory) {
        RegistryStorageContentUtils utils = new RegistryStorageContentUtils();
        utils.factory = factory;
        return utils;
    }

    @Test
    void testCanonicalizeContentPropagatesCanonicalizerFailure() {
        RuntimeException canonicalizerFailure = new IllegalStateException("boom");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenThrow(canonicalizerFailure);

        RegistryStorageContentUtils utils = newUtils(factory);
        TypedContent content = TypedContent.create(ContentHandle.create("{}"), "application/json");

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> utils.canonicalizeContent("AVRO", content, Map.of()));
        Assertions.assertEquals(canonicalizerFailure, ex.getCause());
    }

    @Test
    void testGetCanonicalContentHashPropagatesCanonicalizerFailure() {
        RuntimeException canonicalizerFailure = new IllegalStateException("boom");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenThrow(canonicalizerFailure);

        RegistryStorageContentUtils utils = newUtils(factory);
        TypedContent content = TypedContent.create(ContentHandle.create("{}"), "application/json");

        Assertions.assertThrows(RegistryException.class,
                () -> utils.getCanonicalContentHash(content, "AVRO", null, null));
    }
}
