/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    /**
     * The cause must be exactly the exception thrown by the canonicalizer (not e.g. an unrelated
     * NPE from a mis-set-up mock).
     */
    @Test
    void testCanonicalizeContentPropagatesCanonicalizerFailure() {
        IllegalStateException canonicalizerFailure = new IllegalStateException("simulated canonicalizer failure");

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

        Assertions.assertSame(canonicalizerFailure, ex.getCause());
        verify(canonicalizer).canonicalize(any(), any());
    }

    /**
     * The reference-resolver overload delegates to the Map-based overload above, which already
     * wraps canonicalizer failures in a RegistryException. This must not be wrapped a second time.
     */
    @Test
    void testCanonicalizeContentWithReferenceResolverDoesNotDoubleWrap() {
        IllegalStateException canonicalizerFailure = new IllegalStateException("simulated canonicalizer failure");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenThrow(canonicalizerFailure);

        RegistryStorageContentUtils utils = newUtils(factory);
        TypedContent content = TypedContent.create(ContentHandle.create("{}"), "application/json");
        List<ArtifactReferenceDto> references = List.of();

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> utils.canonicalizeContent("AVRO", content, references, refs -> Map.of()));

        Assertions.assertSame(canonicalizerFailure, ex.getCause());
    }

    /**
     * Canonical content hash computation must fail, not silently hash the raw content, when the
     * underlying canonicalizer fails.
     */
    @Test
    void testGetCanonicalContentHashPropagatesCanonicalizerFailure() {
        IllegalStateException canonicalizerFailure = new IllegalStateException("simulated canonicalizer failure");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenThrow(canonicalizerFailure);

        RegistryStorageContentUtils utils = newUtils(factory);
        TypedContent content = TypedContent.create(ContentHandle.create("{}"), "application/json");

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> utils.getCanonicalContentHash(content, "AVRO", null, null));

        Assertions.assertSame(canonicalizerFailure, ex.getCause());
    }

    /**
     * Success-path regression test: proves the error-handling changes did not alter the happy
     * path - the canonicalizer is invoked and its output is returned unmodified.
     */
    @Test
    void testCanonicalizeContentSuccess() {
        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        TypedContent content = TypedContent.create(ContentHandle.create("{\"raw\":true}"), "application/json");
        TypedContent canonicalizedContent = TypedContent.create(ContentHandle.create("{\"canonical\":true}"),
                "application/json");
        Map<String, TypedContent> resolvedReferences = Map.of();

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(content, resolvedReferences)).thenReturn(canonicalizedContent);

        RegistryStorageContentUtils utils = newUtils(factory);

        TypedContent result = utils.canonicalizeContent("AVRO", content, resolvedReferences);

        Assertions.assertSame(canonicalizedContent, result);
        verify(canonicalizer).canonicalize(content, resolvedReferences);
    }

    /**
     * A failure resolving the artifact type provider (e.g. an unknown/unsupported artifact type)
     * must be distinguished from a canonicalizer execution failure - the canonicalizer itself
     * must never be invoked in this case.
     */
    @Test
    void testCanonicalizeContentProviderLookupFailure() {
        IllegalArgumentException lookupFailure = new IllegalArgumentException("Unknown artifact type: BOGUS");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);
        when(factory.getArtifactTypeProvider("BOGUS")).thenThrow(lookupFailure);

        RegistryStorageContentUtils utils = newUtils(factory);
        TypedContent content = TypedContent.create(ContentHandle.create("{}"), "application/json");

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> utils.canonicalizeContent("BOGUS", content, Map.of()));

        Assertions.assertSame(lookupFailure, ex.getCause());
        verifyNoInteractions(canonicalizer);
    }
}
