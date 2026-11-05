package io.apicurio.registry.references;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReferenceResolutionTest {

    /**
     * Verifies that resolved references keep the content type of the referenced content (for example
     * application/yaml) rather than its artifact type (regression test for #10428).
     */
    @Test
    void testRecursivelyResolveReferencesUsesContentType() {
        ContentWrapperDto yamlReference = ContentWrapperDto.builder()
                .content(ContentHandle.create("openapi: 3.0.3\ninfo:\n  title: Common API\n  version: 1.0.0\npaths: {}\n"))
                .contentType(ContentTypes.APPLICATION_YAML).artifactType(ArtifactType.OPENAPI)
                .references(Collections.emptyList()).build();
        ArtifactReferenceDto reference = ArtifactReferenceDto.builder().groupId("default")
                .artifactId("common-api").version("1").name("common-api.yaml").build();

        Map<String, TypedContent> resolved = ReferenceResolution
                .recursivelyResolveReferences(List.of(reference), ref -> yamlReference);

        Assertions.assertEquals(1, resolved.size());
        Assertions.assertEquals(ContentTypes.APPLICATION_YAML,
                resolved.get("common-api.yaml").getContentType());
    }

    /** Root references a, b, c; a references d, b references e. */
    private static RecordingAuthorizingLoader graph(Set<String> denied) {
        return new RecordingAuthorizingLoader(denied).with("a", "d").with("b", "e");
    }

    private static final List<ArtifactReferenceDto> ROOTS = List.of(RecordingAuthorizingLoader.ref("a"),
            RecordingAuthorizingLoader.ref("b"), RecordingAuthorizingLoader.ref("c"));

    @Test
    void eachLevelIsAuthorizedInOneCallBeforeAnyOfItIsLoaded() {
        RecordingAuthorizingLoader loader = graph(Set.of());

        Map<String, TypedContent> resolved = ReferenceResolution.recursivelyResolveReferences(ROOTS, loader);

        Assertions.assertEquals(List.of(List.of("a", "b", "c"), List.of("d", "e")), loader.authorizedBatches);
        Assertions.assertEquals(List.of("a", "b", "c", "d", "e"), loader.loaded);
        // The name map flattens the whole graph
        Assertions.assertEquals(Set.of("a", "b", "c", "d", "e"), resolved.keySet());
    }

    @Test
    void aDeniedReferenceFailsTheWalkBeforeItsLevelIsLoaded() {
        RecordingAuthorizingLoader denyRoot = graph(Set.of("c"));
        Assertions.assertThrows(ForbiddenException.class,
                () -> ReferenceResolution.recursivelyResolveReferences(ROOTS, denyRoot));
        Assertions.assertEquals(List.of(), denyRoot.loaded);

        // A denied nested reference fails too: nothing of its level is loaded
        RecordingAuthorizingLoader denyNested = graph(Set.of("e"));
        Assertions.assertThrows(ForbiddenException.class,
                () -> ReferenceResolution.recursivelyResolveReferences(ROOTS, denyNested));
        Assertions.assertEquals(List.of("a", "b", "c"), denyNested.loaded);
    }
}
