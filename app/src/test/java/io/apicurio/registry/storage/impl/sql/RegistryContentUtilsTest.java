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
import io.apicurio.registry.references.RecordingAuthorizingLoader;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

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

    // ==================== Authorizing references while resolving them ====================

    private static RegistryContentUtils.RewrittenContentHolder resolve(RecordingAuthorizingLoader loader,
            List<ArtifactReferenceDto> references) {
        ArtifactTypeUtilProviderFactory factory = mock(ArtifactTypeUtilProviderFactory.class, RETURNS_DEEP_STUBS);
        return RegistryContentUtils.recursivelyResolveReferencesWithContext(factory,
                TypedContent.create(ContentHandle.create("{}"), ContentTypes.APPLICATION_JSON), ArtifactType.JSON,
                references, loader);
    }

    private static final List<ArtifactReferenceDto> ROOTS = List.of(RecordingAuthorizingLoader.ref("a"),
            RecordingAuthorizingLoader.ref("b"), RecordingAuthorizingLoader.ref("c"));

    @Test
    void theReferencesOfEachDocumentAreAuthorizedInOneCallBeforeAnyIsLoaded() {
        RecordingAuthorizingLoader loader = new RecordingAuthorizingLoader(Set.of()).with("a", "d", "e");

        resolve(loader, ROOTS);

        // Depth-first: the roots, then a's references when a is loaded
        Assertions.assertEquals(List.of(List.of("a", "b", "c"), List.of("d", "e")), loader.authorizedBatches);
        Assertions.assertEquals(List.of("a", "d", "e", "b", "c"), loader.loaded);
    }

    @Test
    void aDeniedSiblingFailsBeforeAnyOfItsSiblingsIsLoaded() {
        RecordingAuthorizingLoader loader = new RecordingAuthorizingLoader(Set.of("c"));

        Assertions.assertThrows(ForbiddenException.class, () -> resolve(loader, ROOTS));
        Assertions.assertEquals(List.of(), loader.loaded);
    }

    @Test
    void anInvalidReferenceIsStillReportedAsInvalidRatherThanAuthorizedInBulk() {
        RecordingAuthorizingLoader loader = new RecordingAuthorizingLoader(Set.of("b"));
        ArtifactReferenceDto invalid = ArtifactReferenceDto.builder().groupId("g").artifactId("x").name("x").build();

        Assertions.assertThrows(IllegalStateException.class,
                () -> resolve(loader, List.of(invalid, RecordingAuthorizingLoader.ref("b"))));
        Assertions.assertEquals(List.of(), loader.authorizedBatches);
    }
}
