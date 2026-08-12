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
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.storage.ReferenceResolutionConfigProperties;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void testRecursivelyResolveReferencesHandlesCircularReferences() {
        Map<String, TestNode> nodes = new HashMap<>();
        nodes.put("A", new TestNode("A", List.of(reference("B"))));
        nodes.put("B", new TestNode("B", List.of(reference("A"))));
        AtomicInteger loadCount = new AtomicInteger();

        Map<String, String> resolved = RegistryContentUtils.recursivelyResolveReferencesGeneric(
                () -> List.of(reference("A")),
                ArtifactReferenceDto::getName,
                reference -> {
                    loadCount.incrementAndGet();
                    return nodes.get(reference.getName());
                },
                TestNode::value
        );

        Assertions.assertEquals(2, loadCount.get());
        Assertions.assertEquals(2, resolved.size());
        Assertions.assertEquals("A", resolved.get("A"));
        Assertions.assertEquals("B", resolved.get("B"));
    }

    @Test
    void testRecursivelyResolveReferencesStopsAtMaximumDepth() {
        int chainLength = ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 2;
        Map<String, TestNode> nodes = new HashMap<>();
        for (int i = 0; i < chainLength; i++) {
            String name = "node-" + i;
            List<ArtifactReferenceDto> references = i + 1 < chainLength
                    ? List.of(reference("node-" + (i + 1))) : List.of();
            nodes.put(name, new TestNode(name, references));
        }
        AtomicInteger loadCount = new AtomicInteger();

        Map<String, String> resolved = RegistryContentUtils.recursivelyResolveReferencesGeneric(
                () -> List.of(reference("node-0")),
                ArtifactReferenceDto::getName,
                reference -> {
                    loadCount.incrementAndGet();
                    return nodes.get(reference.getName());
                },
                TestNode::value
        );

        Assertions.assertEquals(ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 1, loadCount.get());
        Assertions.assertEquals(ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 1, resolved.size());
        Assertions.assertEquals("node-0", resolved.get("node-0"));
        Assertions.assertEquals("node-" + ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH,
                resolved.get("node-" + ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH));
        Assertions.assertNull(resolved.get("node-"
                + (ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 1)));
    }

    @Test
    void testRecursivelyResolveReferencesWithContextHandlesCircularDereference() {
        Map<String, ContentWrapperDto> nodes = new HashMap<>();
        nodes.put("A", contentWrapper("A", "B"));
        nodes.put("B", contentWrapper("B", "A"));
        ArtifactTypeUtilProviderFactory factory = contextAwareFactory();
        AtomicInteger loadCount = new AtomicInteger();

        RegistryContentUtils.RewrittenContentHolder result = Assertions.assertDoesNotThrow(() ->
                RegistryContentUtils.recursivelyResolveReferencesWithContext(factory,
                        TypedContent.create("{}", "test"), "test", List.of(reference("A")), reference -> {
                            loadCount.incrementAndGet();
                            return nodes.get(reference.getArtifactId());
                        }));

        Assertions.assertEquals(2, loadCount.get());
        Assertions.assertEquals(2, result.getResolvedReferences().size());
    }

    @Test
    void testRecursivelyResolveReferencesWithContextStopsAtMaximumDepth() {
        int chainLength = ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 2;
        Map<String, ContentWrapperDto> nodes = new HashMap<>();
        for (int i = 0; i < chainLength; i++) {
            String artifactId = "node-" + i;
            String referencedArtifactId = i + 1 < chainLength ? "node-" + (i + 1) : null;
            nodes.put(artifactId, contentWrapper(artifactId, referencedArtifactId));
        }
        ArtifactTypeUtilProviderFactory factory = contextAwareFactory();
        AtomicInteger loadCount = new AtomicInteger();

        RegistryContentUtils.RewrittenContentHolder result = RegistryContentUtils
                .recursivelyResolveReferencesWithContext(factory, TypedContent.create("{}", "test"), "test",
                        List.of(reference("node-0")), reference -> {
                            loadCount.incrementAndGet();
                            return nodes.get(reference.getArtifactId());
                        });

        Assertions.assertEquals(ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 1, loadCount.get());
        Assertions.assertEquals(ReferenceResolutionConfigProperties.DEFAULT_MAX_DEPTH + 1,
                result.getResolvedReferences().size());
    }

    private static ArtifactTypeUtilProviderFactory contextAwareFactory() {
        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class);
        ArtifactTypeUtilProvider provider = mock(ArtifactTypeUtilProvider.class);
        ContentDereferencer dereferencer = mock(ContentDereferencer.class);
        when(factory.getArtifactTypeProvider("test")).thenReturn(provider);
        when(provider.getContentDereferencer()).thenReturn(dereferencer);
        when(dereferencer.rewriteReferences(any(TypedContent.class), any())).thenAnswer(invocation ->
                invocation.getArgument(0));
        return factory;
    }

    private static ContentWrapperDto contentWrapper(String artifactId, String referencedArtifactId) {
        List<ArtifactReferenceDto> references = referencedArtifactId == null ? List.of()
                : List.of(reference(referencedArtifactId));
        return ContentWrapperDto.builder()
                .artifactType("test")
                .content(ContentHandle.create(artifactId))
                .references(references)
                .build();
    }

    private static ArtifactReferenceDto reference(String name) {
        return ArtifactReferenceDto.builder()
                .artifactId(name)
                .version("1")
                .name(name)
                .build();
    }

    private record TestNode(String value, List<ArtifactReferenceDto> references)
            implements RegistryContentUtils.HasReferences {
        @Override
        public List<ArtifactReferenceDto> getReferences() {
            return references;
        }
    }
}
