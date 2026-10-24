package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ReferenceGraphWalkerTest {

    /**
     * Regression test for #9527: a reference cycle ({@code A -> B -> A}) must not recurse forever. Adapted
     * from Ananya44444's {@code testRecursivelyResolveReferencesHandlesCircularReferences} written for
     * #9528 against the generic walker this replaces (credit for the original test case: each coordinate
     * is loaded exactly once, and both ends of the cycle resolve).
     */
    @Test
    void walkHandlesCircularReferences() {
        ArtifactReferenceDto refB = reference("B");
        ArtifactReferenceDto refA = reference("A");
        Map<GAV, List<ArtifactReferenceDto>> referencesByCoordinates = Map.of(gav("A"), List.of(refB),
                gav("B"), List.of(refA));
        AtomicInteger loadCount = new AtomicInteger();

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                loadCount.incrementAndGet();
                loaded.put(target,
                        ReferenceNode.builder().coordinates(target)
                                .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                                .references(referencesByCoordinates.get(target)).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(refA), loader, 100);

        Assertions.assertEquals(2, loadCount.get());
        Assertions.assertEquals(2, resolved.coordinates().size());
        Map<String, TypedContent> nameMap = resolved.asNameMap();
        Assertions.assertEquals("A", nameMap.get("A").getContent().content());
        Assertions.assertEquals("B", nameMap.get("B").getContent().content());
        Assertions.assertTrue(resolved.findCycle().isPresent());
    }

    @Test
    void walkStopsAtMaximumDepth() {
        int chainLength = 5;
        Map<GAV, ArtifactReferenceDto> nextByCoordinates = new LinkedHashMap<>();
        for (int i = 0; i < chainLength; i++) {
            ArtifactReferenceDto next = i + 1 < chainLength ? reference("node-" + (i + 1)) : null;
            nextByCoordinates.put(gav("node-" + i), next);
        }

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                ArtifactReferenceDto next = nextByCoordinates.get(target);
                loaded.put(target,
                        ReferenceNode.builder().coordinates(target)
                                .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                                .references(next == null ? List.of() : List.of(next)).build());
            }
            return loaded;
        };

        Assertions.assertThrows(ReferenceDepthExceededException.class,
                () -> ReferenceGraphWalker.walk(List.of(reference("node-0")), loader, chainLength - 2));
    }

    private static GAV gav(String artifactId) {
        return new GAV("default", artifactId, "1");
    }

    private static ArtifactReferenceDto reference(String name) {
        return ArtifactReferenceDto.builder().groupId("default").artifactId(name).version("1").name(name)
                .build();
    }
}
