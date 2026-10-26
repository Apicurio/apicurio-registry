package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ReferenceNodeTest {

    /**
     * Regression test (review of #10716 at 79061fc, optional finding O1): {@link ReferenceNode#getReferences()}
     * must be immune to the caller mutating the list it passed to the builder after {@code build()}, and to
     * the caller mutating one of that list's {@link ArtifactReferenceDto} elements, which stays mutable via
     * {@code @Setter} unless the node copies the element too, not just the list.
     */
    @Test
    void referencesAreUnaffectedByMutatingTheSourceListOrItsElements() {
        ArtifactReferenceDto reference = ArtifactReferenceDto.builder().groupId("g").artifactId("a")
                .version("1").name("original-name").build();
        List<ArtifactReferenceDto> source = new ArrayList<>();
        source.add(reference);

        ReferenceNode node = ReferenceNode.builder().coordinates(new GAV("g", "a", "1"))
                .content(TypedContent.create("content", "text/plain")).references(source).build();

        // Mutate the source list after build(): adding to it must not change the node's view.
        source.add(ArtifactReferenceDto.builder().groupId("g").artifactId("b").version("1").name("added")
                .build());
        Assertions.assertEquals(1, node.getReferences().size());

        // Mutate the element itself after build(): the node's copy must not change.
        reference.setName("mutated-name");
        Assertions.assertEquals("original-name", node.getReferences().get(0).getName());

        // The returned list itself must be unmodifiable.
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> node.getReferences().add(reference));
    }
}
