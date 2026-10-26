package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Boundary test (review of #10716 at 79061fc, optional finding O2): a chain of exactly {@code maxDepth}
     * levels must resolve, not throw. The walker is 1-based (root's direct references are depth 1,
     * {@code depth > maxDepth} throws), unlike the pre-#10634 SQL resolver it replaces for this call site,
     * which was 0-based and so allowed one level more than its configured limit; a chain that depended on
     * that extra level is a behaviour change, not a bug in this walker.
     */
    @Test
    void walkResolvesAChainOfExactlyMaximumDepth() {
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

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(reference("node-0")), loader,
                chainLength);

        Assertions.assertEquals(chainLength, resolved.coordinates().size());
    }

    /**
     * Regression test for a Copilot review finding on #10716: a cycle whose closing edge points back to a
     * coordinate already loaded at a shallower depth must not fail the depth limit, even when the cycle's
     * own length equals {@code maxDepth}. {@code A -> B -> A} with {@code maxDepth = 2}: A is loaded at
     * depth 1, B at depth 2, and the edge B -> A at depth 3 is a dedup against the already-loaded A, not a
     * newly discovered coordinate, so it must not throw.
     */
    @Test
    void walkDoesNotFailDepthLimitOnACycleClosingEdge() {
        ArtifactReferenceDto refB = reference("B");
        ArtifactReferenceDto refA = reference("A");
        Map<GAV, List<ArtifactReferenceDto>> referencesByCoordinates = Map.of(gav("A"), List.of(refB),
                gav("B"), List.of(refA));

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                loaded.put(target,
                        ReferenceNode.builder().coordinates(target)
                                .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                                .references(referencesByCoordinates.get(target)).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(refA), loader, 2);

        Assertions.assertEquals(2, resolved.coordinates().size());
        Assertions.assertTrue(resolved.findCycle().isPresent());
    }

    /**
     * Regression test for a Copilot review finding on #10716: {@link ResolvedReferences#asNameMap()} used
     * to keep only one name per target, dropping legitimate aliases — two different reference names
     * pointing at the same resolved version, which is not the same-name/different-target collision #9000
     * defers. Both {@code a.proto} and {@code b.proto} here target the same version and must both appear.
     */
    @Test
    void asNameMapKeepsEveryAliasOfTheSameTarget() {
        ArtifactReferenceDto refA = reference("a.proto", "shared");
        ArtifactReferenceDto refB = reference("b.proto", "shared");

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                loaded.put(target, ReferenceNode.builder().coordinates(target)
                        .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                        .references(List.of()).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(refA, refB), loader, 100);

        Map<String, TypedContent> nameMap = resolved.asNameMap();
        Assertions.assertEquals(2, nameMap.size());
        Assertions.assertEquals("shared", nameMap.get("a.proto").getContent().content());
        Assertions.assertEquals("shared", nameMap.get("b.proto").getContent().content());
    }

    /**
     * Regression test for two Copilot review findings on #10716: {@link ResolvedReferences#findCycle()} and
     * its dependency-first order computation used plain recursion over the already-resolved graph, so a
     * root that references every node of a long chain directly — giving every node breadth-first depth 1,
     * well within any reasonable {@code maxDepth} — could still overflow the stack during the separate,
     * in-memory post-order pass. Both an acyclic and (in a second case) a cyclic long chain must complete
     * without a {@code StackOverflowError}.
     */
    @Test
    void longDependencyChainWithShallowDiscoveryDepthDoesNotOverflowTheStack() {
        int chainLength = 20_000;
        List<ArtifactReferenceDto> rootReferences = new ArrayList<>();
        Map<GAV, ArtifactReferenceDto> nextByCoordinates = new LinkedHashMap<>();
        for (int i = 0; i < chainLength; i++) {
            // Every node is directly referenced from the root (breadth-first depth 1), but node i also
            // references node i + 1, forming a long dependency chain the post-order pass must traverse.
            rootReferences.add(reference("node-" + i));
            if (i + 1 < chainLength) {
                nextByCoordinates.put(gav("node-" + i), reference("node-" + (i + 1)));
            }
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

        ResolvedReferences resolved = ReferenceGraphWalker.walk(rootReferences, loader, chainLength + 1);

        Assertions.assertEquals(chainLength, resolved.coordinates().size());
        Assertions.assertEquals(chainLength, resolved.asNameMap().size());
        Assertions.assertTrue(resolved.findCycle().isEmpty());
    }

    /**
     * The cyclic counterpart of {@link #longDependencyChainWithShallowDiscoveryDepthDoesNotOverflowTheStack()}:
     * the same long, directly-referenced chain, but its last node closes a cycle back to the first. Both
     * {@link ResolvedReferences#findCycle()} and the dependency-first pass it is computed independently of
     * must still complete without overflowing the stack, and the cycle must be found.
     */
    @Test
    void longCyclicDependencyChainWithShallowDiscoveryDepthDoesNotOverflowTheStack() {
        int chainLength = 20_000;
        List<ArtifactReferenceDto> rootReferences = new ArrayList<>();
        Map<GAV, ArtifactReferenceDto> nextByCoordinates = new LinkedHashMap<>();
        for (int i = 0; i < chainLength; i++) {
            rootReferences.add(reference("node-" + i));
            // Node i references node i + 1, except the last node, which closes the cycle back to node 0.
            nextByCoordinates.put(gav("node-" + i), reference("node-" + ((i + 1) % chainLength)));
        }

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                ArtifactReferenceDto next = nextByCoordinates.get(target);
                loaded.put(target,
                        ReferenceNode.builder().coordinates(target)
                                .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                                .references(List.of(next)).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(rootReferences, loader, chainLength + 1);

        Assertions.assertEquals(chainLength, resolved.coordinates().size());
        Assertions.assertTrue(resolved.findCycle().isPresent());
    }

    /**
     * Regression test for design question 4 (resolved 2026-10-23, pinned by review of #10716 at 79061fc):
     * {@link ResolvedReferences#asNameMap()} and {@link ResolvedReferences#dependencyFirstOrder()} must
     * order entries dependency-first (post-order), not in breadth-first discovery order. {@code root -> a
     * -> b}: {@code b} must appear before {@code a} in both.
     */
    @Test
    void dependencyFirstOrderPlacesDependenciesBeforeDependents() {
        ArtifactReferenceDto refB = reference("b");
        ArtifactReferenceDto refA = reference("a");
        Map<GAV, List<ArtifactReferenceDto>> referencesByCoordinates = Map.of(gav("a"), List.of(refB),
                gav("b"), List.of());

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                loaded.put(target, ReferenceNode.builder().coordinates(target)
                        .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                        .references(referencesByCoordinates.get(target)).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(refA), loader, 100);

        Assertions.assertEquals(List.of(gav("b"), gav("a")), resolved.dependencyFirstOrder());
        Assertions.assertEquals(List.of("b", "a"), new ArrayList<>(resolved.asNameMap().keySet()));
    }

    /**
     * Regression test for design question 5 (resolved 2026-10-23, pinned by review of #10716 at 79061fc):
     * when two different targets share a reference name, {@link ResolvedReferences#asNameMap()} must pick
     * the one discovered first in breadth-first order — the shallower one — over one reached later through
     * a deeper path. {@code "n"} is declared twice: directly from the root (depth 1, target {@code direct})
     * and, through {@code mid}, one level deeper (depth 2, target {@code deep}); {@code direct} must win.
     * #9000 changes this collapsing behaviour, and must touch this test when it does.
     */
    @Test
    void asNameMapPicksTheBreadthFirstWinnerForASharedName() {
        ArtifactReferenceDto directRef = reference("n", "direct");
        ArtifactReferenceDto midRef = reference("mid", "mid");
        ArtifactReferenceDto deepRef = reference("n", "deep");
        Map<GAV, List<ArtifactReferenceDto>> referencesByCoordinates = Map.of(gav("direct"), List.of(),
                gav("mid"), List.of(deepRef), gav("deep"), List.of());

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                loaded.put(target, ReferenceNode.builder().coordinates(target)
                        .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                        .references(referencesByCoordinates.get(target)).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(directRef, midRef), loader, 100);

        Assertions.assertEquals(1, resolved.depthOf(gav("direct")).orElseThrow());
        Assertions.assertEquals(2, resolved.depthOf(gav("deep")).orElseThrow());
        Assertions.assertEquals("direct", resolved.asNameMap().get("n").getContent().content());
    }

    /**
     * Regression test for {@link ResolvedReferences#missing()} and the documented "name stays open"
     * behaviour of {@link ResolvedReferences#asNameMap()} (pinned by review of #10716 at 79061fc): a first
     * occurrence of a name whose target does not resolve (a deleted, or never-existing, version) must not
     * block a later occurrence of the same name whose target does resolve.
     */
    @Test
    void asNameMapLeavesANameOpenWhenItsFirstOccurrenceIsMissing() {
        ArtifactReferenceDto danglingRef = reference("common", "dangling");
        ArtifactReferenceDto midRef = reference("mid", "mid");
        ArtifactReferenceDto resolvedRef = reference("common", "resolved");
        Map<GAV, List<ArtifactReferenceDto>> referencesByCoordinates = Map.of(gav("mid"), List.of(resolvedRef),
                gav("resolved"), List.of());

        ReferenceLoader loader = coordinates -> {
            Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
            for (GAV target : coordinates) {
                if (target.equals(gav("dangling"))) {
                    // Simulates a deleted, or never-existing, version: the loader has no node for it.
                    continue;
                }
                loaded.put(target, ReferenceNode.builder().coordinates(target)
                        .content(TypedContent.create(target.getRawArtifactId(), "text/plain"))
                        .references(referencesByCoordinates.get(target)).build());
            }
            return loaded;
        };

        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(danglingRef, midRef), loader, 100);

        Assertions.assertEquals(Set.of(gav("dangling")), resolved.missing());
        Assertions.assertEquals("resolved", resolved.asNameMap().get("common").getContent().content());
    }

    private static GAV gav(String artifactId) {
        return new GAV("default", artifactId, "1");
    }

    private static ArtifactReferenceDto reference(String name) {
        return reference(name, name);
    }

    private static ArtifactReferenceDto reference(String name, String artifactId) {
        return ArtifactReferenceDto.builder().groupId("default").artifactId(artifactId).version("1")
                .name(name).build();
    }
}
