/*
 * Copyright 2020 Red Hat Inc
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
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.*;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryContentUtilsTest {

    @Test
    void testSerializeLabels() {
        Map<String, String> props = new HashMap<>();
        props.put("one", "1");
        props.put("two", "2");
        props.put("three", "3");
        String actual = RegistryContentUtils.serializeLabels(props);
        String expected = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testDeserializeLabels() {
        String propsStr = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Map<String, String> actual = RegistryContentUtils.deserializeLabels(propsStr);
        Assertions.assertNotNull(actual);
        Map<String, String> expected = new HashMap<>();
        expected.put("one", "1");
        expected.put("two", "2");
        expected.put("three", "3");
        Assertions.assertEquals(expected, actual);
    }

    /**
     * Regression test for GH #9590: a failing ContentCanonicalizer must not be silently
     * swallowed and must not result in the raw, non-canonicalized content being returned. The
     * cause must be exactly the exception thrown by the canonicalizer (not e.g. an unrelated NPE
     * from a mis-set-up mock), and must not be double-wrapped by the public wrapper method.
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

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create("{}")).contentType("application/json")
                .artifactType("AVRO").references(List.of()).build();

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> RegistryContentUtils.canonicalizeContent(factory, "AVRO", data, ref -> null));

        // Single wrap only: the public wrapper must propagate the RegistryException raised by
        // the canonicalizer invocation as-is, not wrap it a second time.
        Assertions.assertSame(canonicalizerFailure, ex.getCause());
        verify(canonicalizer).canonicalize(any(), any());
    }

    /**
     * Regression test for GH #9590: canonical content hash computation must fail, not
     * silently hash the raw content, when the underlying canonicalizer fails. The cause must be
     * exactly the canonicalizer's exception.
     */
    @Test
    void testCanonicalContentHashPropagatesCanonicalizerFailure() {
        IllegalStateException canonicalizerFailure = new IllegalStateException("simulated canonicalizer failure");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenThrow(canonicalizerFailure);

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create("{}")).contentType("application/json")
                .artifactType("AVRO").references(List.of()).build();

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> RegistryContentUtils.canonicalContentHash(factory, "AVRO", data, ref -> null));

        Assertions.assertSame(canonicalizerFailure, ex.getCause());
    }

    /**
     * Success-path regression test for GH #9590: proves the error-handling changes did not alter
     * the happy path - the canonicalizer is invoked and its output is returned unmodified.
     */
    @Test
    void testCanonicalizeContentSuccess() {
        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        TypedContent canonicalizedContent = TypedContent.create(ContentHandle.create("{\"canonical\":true}"),
                "application/json");

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenReturn(canonicalizedContent);

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create("{\"raw\":true}")).contentType("application/json")
                .artifactType("AVRO").references(List.of()).build();

        TypedContent result = RegistryContentUtils.canonicalizeContent(factory, "AVRO", data, ref -> null);

        Assertions.assertSame(canonicalizedContent, result);
        verify(canonicalizer).canonicalize(any(), any());
    }

    /**
     * Regression test for GH #9590: a failure resolving the artifact type provider (e.g. an
     * unknown/unsupported artifact type) must be distinguished from a canonicalizer execution
     * failure - the canonicalizer itself must never be invoked in this case.
     */
    @Test
    void testCanonicalizeContentProviderLookupFailure() {
        IllegalArgumentException lookupFailure = new IllegalArgumentException("Unknown artifact type: BOGUS");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);
        when(factory.getArtifactTypeProvider("BOGUS")).thenThrow(lookupFailure);

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create("{}")).contentType("application/json")
                .artifactType("BOGUS").references(List.of()).build();

        RegistryException ex = Assertions.assertThrows(RegistryException.class,
                () -> RegistryContentUtils.canonicalizeContent(factory, "BOGUS", data, ref -> null));

        Assertions.assertSame(lookupFailure, ex.getCause());
        verifyNoInteractions(canonicalizer);
    }
}
