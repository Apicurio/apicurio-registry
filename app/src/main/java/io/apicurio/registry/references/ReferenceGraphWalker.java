package io.apicurio.registry.references;

import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks an artifact's reference graph and resolves every version it reaches, replacing the five separate
 * traversal implementations that used to do this on the server, each with its own rules for keying nodes,
 * detecting cycles, limiting depth and handling errors (see {@code DESIGN.md} in the project's tracking
 * folder for the inventory and the bugs that came from them disagreeing — #9000, #9626, #9527, #9095,
 * #9069).
 *
 * <h2>Why breadth-first</h2>
 * The walker loads one level of the graph at a time: it asks its {@link ReferenceLoader} for every
 * coordinate the previous level referenced, in one call, rather than recursing into each reference as soon
 * as it is found. This is what lets a SQL-backed loader batch its queries — one metadata query and one
 * content query per level, instead of one of each per reference — which is the reason walker 4
 * ({@code SqlContentRepository.resolveReferencesRaw}) existed as a separate implementation in the first
 * place. Breadth-first also means a node's first-discovered depth is always its shortest path from the
 * root, which removes the "expand again at a shallower depth" logic the {@code /references/graph} endpoint
 * needed under depth-first traversal.
 * <p>
 * The result, a {@link ResolvedReferences}, in turn exposes a dependency-first (post-order) view computed
 * separately, by a depth-first pass over the already-resolved graph. Breadth-first loading and
 * depth-first presentation are not in tension: the second costs no I/O, because every node it visits was
 * already loaded by the first. See {@link ResolvedReferences}'s class Javadoc for the full reasoning,
 * including how a reference name that resolves to two different targets ({@code #9000}'s bug) is handled
 * in the meantime.
 *
 * <h2>Cycles</h2>
 * A coordinate is marked visited the moment it is first queued for loading, not after it (and everything
 * reachable from it) finishes loading. This is the specific bug this walker fixes relative to the generic
 * walker it replaces (#9527): that one checked its visited map before recursing but only populated it
 * after returning from the recursive call, so a cycle such as {@code A → B → A} was not yet in the map
 * when the walk returned to {@code A}, and recursed forever. Marking visited at first-queue time means a
 * cycle simply stops the walk from loading the same branch twice — it does not fail the walk by itself.
 * Callers that need to know whether a cycle exists, and where, use {@link ResolvedReferences#findCycle()}.
 *
 * <h2>Depth limit</h2>
 * {@code apicurio.storage.references.max-depth} applies to every walk (not only the ones that used it
 * before, see {@code DESIGN.md}, open question 3). Exceeding it throws {@link
 * ReferenceDepthExceededException} rather than silently truncating the graph: a truncated graph used to be
 * returned as if it were complete, with only a log warning to say otherwise.
 *
 * <h2>Errors</h2>
 * The walker does not catch anything its {@link ReferenceLoader} throws — a loader exception propagates
 * out of {@link #walk} immediately. The generic walker this replaces swallowed every loader exception and
 * logged it, which is what let {@code REFS_EXIST} report a reference as "does not exist" when loading it
 * had actually failed for an unrelated reason (#9626). Callers that still want "skip on error" behaviour
 * wrap their {@link ReferenceLoader} themselves, the same way {@code RegistryContentUtils}'s thin wrapper
 * methods do for the pre-#10634 entry points — see #9626 for removing that wrapper once every caller has
 * been updated to handle errors on its own terms.
 */
public final class ReferenceGraphWalker {

    private ReferenceGraphWalker() {
    }

    /**
     * Walks the reference graph starting from {@code rootReferences} — typically the references declared
     * by some content that is itself not part of the graph being resolved (the root is never a node of its
     * own {@link ResolvedReferences}; only what it references is).
     *
     * @param rootReferences the references to start from; {@code null} or empty is a valid, empty graph
     * @param loader resolves each level of the graph; see {@link ReferenceLoader}
     * @param maxDepth the deepest level this walk is allowed to reach; typically
     *            {@code apicurio.storage.references.max-depth}
     * @return every reachable artifact version, resolved or recorded as missing
     * @throws ReferenceDepthExceededException if the graph is deeper than {@code maxDepth}
     */
    public static ResolvedReferences walk(List<ArtifactReferenceDto> rootReferences, ReferenceLoader loader,
            int maxDepth) {
        Map<GAV, ReferenceNode> nodes = new LinkedHashMap<>();
        Set<GAV> missing = new LinkedHashSet<>();
        Map<GAV, Integer> depths = new LinkedHashMap<>();
        List<ResolvedReferences.DiscoveredEdge> discoveryOrder = new ArrayList<>();
        Set<GAV> queued = new HashSet<>();

        List<ArtifactReferenceDto> currentLevel = rootReferences == null ? List.of() : rootReferences;
        int depth = 1;

        while (!currentLevel.isEmpty()) {
            if (depth > maxDepth) {
                throw new ReferenceDepthExceededException(maxDepth);
            }

            List<GAV> distinctTargets = new ArrayList<>();
            for (ArtifactReferenceDto reference : currentLevel) {
                if (reference.getName() == null) {
                    throw new IllegalStateException("Invalid reference: " + reference);
                }
                GAV target = coordinatesOf(reference);
                discoveryOrder.add(new ResolvedReferences.DiscoveredEdge(reference.getName(), target));
                if (queued.add(target)) {
                    distinctTargets.add(target);
                }
            }

            Map<GAV, ReferenceNode> loaded = distinctTargets.isEmpty() ? Map.of()
                    : loader.loadBatch(distinctTargets);

            List<ArtifactReferenceDto> nextLevel = new ArrayList<>();
            for (GAV target : distinctTargets) {
                ReferenceNode node = loaded.get(target);
                if (node == null) {
                    missing.add(target);
                } else {
                    nodes.put(target, node);
                    depths.put(target, depth);
                    if (node.getReferences() != null) {
                        nextLevel.addAll(node.getReferences());
                    }
                }
            }

            currentLevel = nextLevel;
            depth++;
        }

        return new ResolvedReferences(nodes, missing, depths, discoveryOrder);
    }

    /**
     * The coordinates an {@link ArtifactReferenceDto} points at. {@link GAV} lives in {@code common} and
     * cannot depend on the storage DTOs in {@code app}, so this conversion lives here instead.
     */
    public static GAV coordinatesOf(ArtifactReferenceDto reference) {
        return new GAV(reference.getGroupId(), reference.getArtifactId(), reference.getVersion());
    }
}
