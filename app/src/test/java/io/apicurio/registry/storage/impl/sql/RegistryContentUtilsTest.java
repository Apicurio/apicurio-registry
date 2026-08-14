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
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
     * swallowed and must not result in the raw, non-canonicalized content being returned.
     */
    @Test
    void testCanonicalizeContentPropagatesCanonicalizerFailure() {
        RuntimeException canonicalizerFailure = new IllegalStateException("boom");

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

        // The public wrapper re-wraps the failure raised internally, so the original
        // canonicalizer failure is preserved a level deeper in the cause chain.
        Assertions.assertNotNull(ex.getCause());
        Assertions.assertEquals(canonicalizerFailure, ex.getCause().getCause());
    }

    /**
     * Regression test for GH #9590: canonical content hash computation must fail, not
     * silently hash the raw content, when the underlying canonicalizer fails.
     */
    @Test
    void testCanonicalContentHashPropagatesCanonicalizerFailure() {
        RuntimeException canonicalizerFailure = new IllegalStateException("boom");

        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentCanonicalizer canonicalizer = mock(ContentCanonicalizer.class);

        when(factory.getArtifactTypeProvider("AVRO")).thenReturn(provider);
        when(provider.getContentCanonicalizer()).thenReturn(canonicalizer);
        when(canonicalizer.canonicalize(any(), any())).thenThrow(canonicalizerFailure);

        ContentWrapperDto data = ContentWrapperDto.builder()
                .content(ContentHandle.create("{}")).contentType("application/json")
                .artifactType("AVRO").references(List.of()).build();

        Assertions.assertThrows(RegistryException.class,
                () -> RegistryContentUtils.canonicalContentHash(factory, "AVRO", data, ref -> null));
    }
}
