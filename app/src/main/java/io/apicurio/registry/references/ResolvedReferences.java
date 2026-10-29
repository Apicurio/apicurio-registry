package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The complete, already-resolved reference graph for one artifact version's content, as produced by
 * {@link ReferenceGraphWalker#walk}.
 * <p>
 * This is a plain, immutable value — a value type in the sense of epic #7815 (VT7: "content with its
 * resolved references"): every reference reachable from the root has already been loaded (or recorded as
 * missing) by the time a {@link ResolvedReferences} exists, and nothing on it triggers further I/O. It
 * holds the whole graph once so that every caller's view of it — a flat name-to-content map for a type
 * handler, the set of content IDs for an ETag, a dependency-first iteration order, cycle detection — is
 * derived from the same data instead of each caller re-walking the graph its own way, which is what the
 * eight separate walkers this replaces used to do (see the milestone epic #10635 for the inventory of
 * those eight and the bugs that came from their disagreeing).
 * <p>
 * Most of these views are pure graph structure — no I/O, no external dependency, cheap regardless of
 * whether a caller asks for them. {@link #dereferenceAll(ArtifactTypeUtilProviderFactory)} is the one
 * exception: it executes real per-artifact-type dereferencing logic (schema parsing and the like) for
 * every node, which is real work, not free. It costs nothing to a caller that never calls it — the content
 * handed in when this type was built is never touched until something asks for the dereferenced view — but
 * it is heavier than this type's other methods, and only worth calling once per use, not per node.
 *
 * <h2>Missing is not the same as failed</h2>
 * A reference whose target does not exist (an artifact version that was deleted, or never existed) is
 * recorded in {@link #missing()}. This is deliberately distinct from a loader throwing: a real error (a
 * database error, for instance) propagates out of {@link ReferenceGraphWalker#walk} immediately and there is
 * no {@link ResolvedReferences} to ask. A missing coordinate is not an error by itself — some callers (for
 * example rule evaluation, resolving references for compatibility checking) are fine skipping it; others
 * (an export, or a database upgrade validating that every reference still resolves) must treat it as a
 * failure. That decision belongs to the caller, not to this type or to the walker: {@link #missing()} is
 * simply the fact, and {@link #asNameMap()} silently omits missing targets the same way the walker this
 * replaces did, because changing that is out of scope for #10634 — #9626 is what removes the silent-skip
 * behaviour.
 *
 * <h2>How names are resolved, and in what order</h2>
 * A reference is named at the edge, not at the node: the same artifact version can be reached under
 * different names from different parents, and conversely two different targets can share a name if they
 * sit in different branches of the graph. {@link #asNameMap()} has to collapse that down to one
 * {@code Map<String, TypedContent>}, because that is the contract every content type handler
 * ({@code ContentDereferencer}, {@code ContentCanonicalizer}, {@code ContentValidator}, and others) takes.
 * Two decisions go into building it, made independently of each other:
 * <ul>
 * <li><b>Which target wins a shared name.</b> Decided in breadth-first discovery order — the order the
 * walker itself loaded references in, level by level. The first occurrence of a name whose target actually
 * resolved (is not in {@link #missing()}) wins; if that occurrence's target is itself missing, the name
 * stays open for a later occurrence, exactly as the walker this replaces behaved. Two different targets
 * sharing a name is already a bug: fixed for every in-tree caller by {@link ReferenceResolution}'s
 * three-argument {@code recursivelyResolveReferences} overload, which calls {@link #dereferenceAll} instead
 * of ever building this flattened, whole-graph view (#9000) — this method's own winner-picking stays for
 * whatever still calls {@link #asNameMap()} directly, logging a warning the first time it sees a genuine
 * collision. Breadth-first order can pick a different winner than the old depth-first code did in that
 * case, and that is an accepted, deliberate behaviour change rather than an attempt to preserve what was an
 * arbitrary choice to begin with. Pinned by {@code
 * ReferenceGraphWalkerTest#asNameMapPicksTheBreadthFirstWinnerForASharedName}.</li>
 * <li><b>What order the winning entries appear in the returned map.</b> Dependency-first (post-order): a
 * node appears only after every node it references. This is computed as a second, separate, in-memory-only
 * pass over the already-resolved graph — a depth-first walk from the root's direct references, which costs
 * nothing in I/O because every node it visits is already loaded. It matches the order the walker this
 * replaces produced, which at least one content type handler historically depended on. Pinned by {@code
 * ReferenceGraphWalkerTest#dependencyFirstOrderPlacesDependenciesBeforeDependents}.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Immutable and safe to share across threads once built.
 */
public final class ResolvedReferences {

    private static final Logger log = LoggerFactory.getLogger(ResolvedReferences.class);

    private final Map<GAV, ReferenceNode> nodes;
    private final Set<GAV> missing;
    private final Map<GAV, Integer> depths;
    private final List<DiscoveredEdge> discoveryOrder;

    ResolvedReferences(Map<GAV, ReferenceNode> nodes, Set<GAV> missing, Map<GAV, Integer> depths,
            List<DiscoveredEdge> discoveryOrder) {
        this.nodes = Collections.unmodifiableMap(nodes);
        this.missing = Collections.unmodifiableSet(missing);
        this.depths = Collections.unmodifiableMap(depths);
        this.discoveryOrder = Collections.unmodifiableList(discoveryOrder);
    }

    /**
     * An edge discovered while walking: a reference named {@code name}, found on some node already in the
     * graph, pointing at {@code target}. Recorded in breadth-first discovery order so that
     * {@link #asNameMap()} can replay that order later without re-walking anything.
     */
    record DiscoveredEdge(String name, GAV target) {
    }

    /**
     * The artifact version at {@code coordinates}, if it was reachable from the root and resolved — that
     * is, if it is neither missing nor simply absent because the graph never reached it.
     */
    public Optional<ReferenceNode> get(GAV coordinates) {
        return Optional.ofNullable(nodes.get(coordinates));
    }

    /**
     * Every coordinate that resolved. Does not include the root itself (the walk starts from the root's
     * references, the root is never a node of its own graph) or any coordinate in {@link #missing()}.
     */
    public Set<GAV> coordinates() {
        return nodes.keySet();
    }

    /**
     * Coordinates that were referenced somewhere in the graph but did not resolve to an existing artifact
     * version. See the class Javadoc on why this is distinct from an error.
     */
    public Set<GAV> missing() {
        return missing;
    }

    /**
     * How many levels deep {@code coordinates} was first reached from the root (the root's direct
     * references are depth 1). A coordinate reachable by more than one path is recorded at the shallowest
     * depth it was found at, since breadth-first discovery guarantees the first time a coordinate is seen
     * is always its shortest path from the root.
     */
    public Optional<Integer> depthOf(GAV coordinates) {
        return Optional.ofNullable(depths.get(coordinates));
    }

    /**
     * The name-to-content view every content type handler's reference resolution contract expects: one
     * entry per distinct reference name reachable from the root, each mapped to the content of whichever
     * target won that name (see the class Javadoc for how winners and ordering are decided), in
     * dependency-first order. A missing target's name is simply absent, matching the walker this replaces.
     * <p>
     * Two different names can legitimately target the same resolved version (for example {@code a.proto}
     * and {@code b.proto} both referencing one shared file) — that is a normal alias, not the same-name
     * collision this method defers to #9000. Every such alias gets its own entry here, all mapped to that
     * target's content.
     * <p>
     * <b>This method keeps #9000's collision bug deliberately</b> — it is a whole-graph, flattened view by
     * definition, so it cannot be node-aware the way {@link ReferenceResolution}'s own bottom-up
     * orchestration is. No in-tree caller builds its reference map this way any more (see
     * {@link ReferenceResolution#recursivelyResolveReferences(List, java.util.function.Function,
     * io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory)} for the fix), but the method
     * stays public for anything that genuinely wants the flattened view. A genuine collision — the same
     * name resolving to two different, both-resolved targets anywhere in the graph — is logged at
     * {@code WARN} the first time it is seen, so a caller that does use this method can find and rename the
     * colliding reference.
     */
    public Map<String, TypedContent> asNameMap() {
        Map<String, GAV> winners = new LinkedHashMap<>();
        Set<String> warnedNames = new HashSet<>();
        for (DiscoveredEdge edge : discoveryOrder) {
            GAV existingWinner = winners.get(edge.name());
            if (existingWinner == null) {
                if (nodes.containsKey(edge.target())) {
                    winners.put(edge.name(), edge.target());
                }
            } else if (!existingWinner.equals(edge.target()) && nodes.containsKey(edge.target())
                    && warnedNames.add(edge.name())) {
                log.warn(
                        "Reference name '{}' resolves to more than one target ({} and {}) within the same "
                                + "reference graph; keeping {} and silently ignoring the rest for now. "
                                + "Rename one of the colliding references — this will become a hard "
                                + "failure in a future release (see #9000).",
                        edge.name(), existingWinner, edge.target(), existingWinner);
            }
        }
        Map<GAV, List<String>> namesByTarget = new LinkedHashMap<>();
        for (Map.Entry<String, GAV> entry : winners.entrySet()) {
            namesByTarget.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        Map<String, TypedContent> result = new LinkedHashMap<>();
        for (GAV gav : dependencyFirstOrder()) {
            List<String> names = namesByTarget.get(gav);
            if (names != null) {
                TypedContent content = nodes.get(gav).getContent();
                for (String name : names) {
                    result.put(name, content);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Dereferences every node in the graph, bottom-up, each using only its own direct references — the
     * #9000 fix. This is a workaround, not the real fix: dereferencing a document properly belongs to the
     * library that understands its format (JSON Schema, OpenAPI, Avro, ...), which can resolve a reference
     * in the context of the specific node that declared it. Iterating node-by-node here, from outside those
     * libraries, is what #10761 (and its companion, Apitomy/apitomy-data-models#1289) is meant to replace,
     * once the libraries themselves can take node identity into account. Until then, this gets the same
     * correctness result by construction, at the cost of calling each library once per node instead of once
     * for the whole document.
     *
     * <h2>Worked example</h2>
     * {@code Main} references two artifacts, {@code A} and {@code B}. Both of them — independently, in
     * different branches of the graph — happen to name their own reference to a shared dependency
     * {@code "common.avsc"}, but at different versions:
     *
     * <pre>{@code
     * Main
     * ├─ "a.avsc"      -> A
     * │                   └─ "common.avsc" -> Common@1
     * └─ "b.avsc"      -> B
     *                     └─ "common.avsc" -> Common@2
     * }</pre>
     *
     * Before #9000's fix, every node in this whole tree was dereferenced in one flat
     * {@code Map<String, TypedContent>} ({@link #asNameMap()}): {@code A}'s and {@code B}'s own
     * {@code "common.avsc"} entries landed in the <em>same</em> map under the <em>same</em> key, so
     * whichever was resolved second silently overwrote the first — {@code B} would end up dereferenced
     * using {@code Common@1}'s content, even though it correctly declared a reference to {@code Common@2}.
     * <p>
     * This method instead walks {@link #dependencyFirstOrder()} — {@code Common@1}, {@code A},
     * {@code Common@2}, {@code B}, in that order — and builds one small map per node, scoped to only that
     * node's own direct references, immediately dereferencing the node with it and remembering the result
     * for whoever references it next:
     *
     * <pre>{@code
     * result = {}                                                     // empty to start
     *
     * Common@1:  ownScoped = {}                                       // no references of its own
     *            result[Common@1] = dereference(Common@1, {})
     *
     * A:         ownScoped = {"common.avsc" -> result[Common@1]}
     *            result[A] = dereference(A, ownScoped)
     *
     * Common@2:  ownScoped = {}
     *            result[Common@2] = dereference(Common@2, {})
     *
     * B:         ownScoped = {"common.avsc" -> result[Common@2]}
     *            result[B] = dereference(B, ownScoped)
     * }</pre>
     *
     * {@code A}'s {@code ownScoped} map and {@code B}'s are two different {@code Map} instances, built and
     * consumed one call apart — the fact that both happen to use the key {@code "common.avsc"} is never
     * visible to the other. There is no single map anywhere that holds both {@code Common@1} and
     * {@code Common@2} entries at once, so there is nothing left for them to collide in.
     * <p>
     * This works for every artifact type, including ones (OpenAPI, AsyncAPI) whose dereferencing does its
     * own whole-document traversal internally: each one is still called once per node, with that one node's
     * small scoped map, never the whole tree's. A node's own direct references can never collide with each
     * other in the first place — sibling-level duplicate names are already rejected at write time by the
     * {@code content_references} table's primary key — so every one of these per-node maps is
     * collision-free by construction.
     * <p>
     * <b>The result is keyed by coordinates, not names</b>, and covers every node in the graph — it is
     * deliberately not the name-to-content shape {@code ContentDereferencer} and friends expect. A caller
     * that needs that shape, scoped to one node's own reference names (the root's, typically), builds it
     * from this result with {@link #scopedReferences}.
     * <p>
     * <b>Fallback for a type that cannot dereference.</b> A node whose artifact type has no
     * {@code ContentDereferencer} support for this operation (for example {@code XmlReferenceDereferencer},
     * which always throws {@code DereferencingNotSupportedException}), or whose dereferencing fails for any
     * other reason, falls back to that node's own <em>raw</em> content in the result — not left out, not a
     * propagated failure. One node's dereferencing trouble must not fail the whole resolution, the same
     * conservative policy {@link ReferenceResolution} already applies to a loader error. A dependent that
     * references such a node simply inlines its raw, undereferenced content, which is the best available
     * answer when the node's own type cannot do better.
     *
     * @param typeProviderFactory resolves the {@code ContentDereferencer} for each node's own artifact type
     * @return every resolved node's dereferenced content (or, on fallback, its raw content), keyed by
     *         coordinates, in no particular order
     */
    public Map<GAV, TypedContent> dereferenceAll(ArtifactTypeUtilProviderFactory typeProviderFactory) {
        Map<GAV, TypedContent> result = new LinkedHashMap<>();
        for (GAV coordinates : dependencyFirstOrder()) {
            ReferenceNode node = nodes.get(coordinates);
            if (node == null) {
                continue; // Missing: nothing to dereference.
            }
            Map<String, TypedContent> ownScoped = scopedReferences(node.getReferences(), result);
            result.put(coordinates,
                    dereferenceNode(typeProviderFactory, node.getArtifactType(), node.getContent(), ownScoped));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Builds the {@code ownScoped} map from {@link #dereferenceAll}'s worked example: one node's own
     * declared references — never a flattened view of the whole graph — each resolved to its target's
     * content in {@code dereferencedByCoordinate} (typically {@link #dereferenceAll}'s own result, or a
     * work-in-progress version of it). A reference whose target is missing, or not yet present in
     * {@code dereferencedByCoordinate} (should not happen when walking {@link #dereferenceAll}'s own
     * dependency-first order, but handled the same way for safety), is simply absent from the result,
     * matching {@link #asNameMap()}'s existing "missing target, missing name" behaviour.
     * <p>
     * Also how {@link ReferenceResolution} takes the final step after {@link #dereferenceAll}: calling this
     * once more with the <em>root's</em> own reference list (the root is never a node of this graph, so it
     * cannot be looked up by {@link #get}) narrows the whole-graph result down to just the names the root
     * itself declares.
     */
    static Map<String, TypedContent> scopedReferences(List<ArtifactReferenceDto> ownReferences,
            Map<GAV, TypedContent> dereferencedByCoordinate) {
        if (ownReferences == null || ownReferences.isEmpty()) {
            return Map.of();
        }
        Map<String, TypedContent> result = new LinkedHashMap<>();
        for (ArtifactReferenceDto reference : ownReferences) {
            TypedContent content = dereferencedByCoordinate.get(ReferenceGraphWalker.coordinatesOf(reference));
            if (content != null) {
                result.put(reference.getName(), content);
            }
        }
        return result;
    }

    /**
     * Dereferences one node — {@code A} or {@code B} in {@link #dereferenceAll}'s worked example — using
     * only its own, already collision-free scoped map. See {@link #dereferenceAll}'s "Fallback for a type
     * that cannot dereference" section for what happens when the node's own type does not support this or
     * fails.
     */
    private static TypedContent dereferenceNode(ArtifactTypeUtilProviderFactory typeProviderFactory,
            String artifactType, TypedContent content, Map<String, TypedContent> ownScopedReferences) {
        try {
            return typeProviderFactory.getArtifactTypeProvider(artifactType).getContentDereferencer()
                    .dereference(content, ownScopedReferences);
        } catch (Exception ex) {
            log.debug("Could not dereference a node of type {} while resolving references; using its "
                    + "raw content for dependents instead.", artifactType, ex);
            return content;
        }
    }

    /**
     * The content ID of every node reachable from the root, distinct and sorted in natural order. Used for
     * identity views such as an ETag over the whole reference tree, where the exact set of content matters
     * but its order and names do not.
     *
     * @throws IllegalStateException if any resolved node's content ID is {@code null} — only loaders that
     *             supply a content ID for every node can use this view (see {@link ReferenceNode}).
     */
    public List<Long> contentIds() {
        List<Long> ids = new ArrayList<>(nodes.size());
        for (ReferenceNode node : nodes.values()) {
            Long contentId = node.getContentId();
            if (contentId == null) {
                throw new IllegalStateException(
                        "Node " + node.getCoordinates() + " has no content ID; contentIds() requires a "
                                + "ReferenceLoader that supplies one for every node.");
            }
            ids.add(contentId);
        }
        return ids.stream().distinct().sorted().toList();
    }

    /**
     * Every resolved node, in dependency-first (post-order) order: a node appears only after every node it
     * references. Computed as a depth-first walk over the already-resolved graph, starting from the root's
     * direct references in the order they were discovered. See the class Javadoc's "How names are
     * resolved" section for why this is DFS even though the walk itself was BFS.
     * <p>
     * Iterative, not recursive: the graph can be deep enough that a straightforward recursive post-order
     * walk would overflow the stack even though {@link ReferenceGraphWalker}'s own, separate breadth-first
     * depth limit accepted the graph (a wide root referencing every node of a long dependency chain gives
     * every node breadth-first depth 1, but the chain itself can still be arbitrarily long).
     */
    public List<GAV> dependencyFirstOrder() {
        List<GAV> order = new ArrayList<>(nodes.size());
        Set<GAV> visited = new HashSet<>();
        for (DiscoveredEdge edge : discoveryOrder) {
            visitPostOrder(edge.target(), visited, order);
        }
        return order;
    }

    private void visitPostOrder(GAV root, Set<GAV> visited, List<GAV> order) {
        if (!visited.add(root)) {
            return;
        }
        ReferenceNode rootNode = nodes.get(root);
        if (rootNode == null) {
            // Missing: recorded in missing(), nothing further to visit through it.
            return;
        }

        Deque<GAV> stack = new ArrayDeque<>();
        Deque<Iterator<ArtifactReferenceDto>> iterators = new ArrayDeque<>();
        stack.push(root);
        iterators.push(childReferences(rootNode));

        while (!stack.isEmpty()) {
            Iterator<ArtifactReferenceDto> it = iterators.peek();
            if (it.hasNext()) {
                GAV child = ReferenceGraphWalker.coordinatesOf(it.next());
                if (visited.add(child)) {
                    ReferenceNode childNode = nodes.get(child);
                    if (childNode != null) {
                        stack.push(child);
                        iterators.push(childReferences(childNode));
                    }
                    // else missing: nothing to visit through it, nothing added to order.
                }
                // else already visited (or on the current path, i.e. part of a cycle): already handled
                // or being handled further down the stack, nothing more to do here.
            } else {
                order.add(stack.pop());
                iterators.pop();
            }
        }
    }

    private static Iterator<ArtifactReferenceDto> childReferences(ReferenceNode node) {
        List<ArtifactReferenceDto> references = node.getReferences();
        return references == null ? Collections.emptyIterator() : references.iterator();
    }

    /**
     * The path of a cycle in the graph, if one exists: coordinates in the order they are reached, with the
     * coordinate that closes the cycle repeated at the end (so {@code path.get(0).equals(path.get(path.size()
     * - 1))}). Computed by depth-first colouring over the already-resolved graph — linear time, since it
     * never revisits a node once finished with it — independently of how the walk itself loaded the graph.
     * <p>
     * The walker that produced this {@link ResolvedReferences} never fails on a cycle by itself: it marks a
     * coordinate visited the moment it is first queued, so a cycle simply stops the walk from loading that
     * branch again (fixing #9527, where the generic walker this replaces checked its visited map too late
     * and recursed forever). Whether a cycle should be an error is the caller's decision — the
     * {@code NO_CIRCULAR_REFERENCES} rule and the {@code /references/graph} endpoint need the path to report
     * it; most other callers do not care that a cycle exists at all as long as the walk itself terminated.
     * <p>
     * Iterative, not recursive, for the same reason as {@link #dependencyFirstOrder()}: a long acyclic
     * chain must not overflow the stack while this method is confirming it has no cycle.
     */
    public Optional<List<GAV>> findCycle() {
        Map<GAV, Integer> colour = new HashMap<>(); // absent = white, 1 = grey (on the current path), 2 = black (done)
        for (GAV start : nodes.keySet()) {
            if (!colour.containsKey(start)) {
                Optional<List<GAV>> cycle = findCycleFrom(start, colour);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<List<GAV>> findCycleFrom(GAV start, Map<GAV, Integer> colour) {
        Deque<GAV> path = new ArrayDeque<>();
        Deque<Iterator<ArtifactReferenceDto>> iterators = new ArrayDeque<>();
        colour.put(start, 1);
        path.addLast(start);
        iterators.push(childReferences(nodes.get(start)));

        while (!path.isEmpty()) {
            Iterator<ArtifactReferenceDto> it = iterators.peek();
            if (!it.hasNext()) {
                colour.put(path.removeLast(), 2);
                iterators.pop();
                continue;
            }

            GAV target = ReferenceGraphWalker.coordinatesOf(it.next());
            if (!nodes.containsKey(target)) {
                continue; // Missing: no edge to follow.
            }
            int targetColour = colour.getOrDefault(target, 0);
            if (targetColour == 1) {
                List<GAV> cycle = new ArrayList<>();
                boolean onCycle = false;
                for (GAV onPath : path) {
                    if (onPath.equals(target)) {
                        onCycle = true;
                    }
                    if (onCycle) {
                        cycle.add(onPath);
                    }
                }
                cycle.add(target);
                return Optional.of(cycle);
            } else if (targetColour == 0) {
                colour.put(target, 1);
                path.addLast(target);
                iterators.push(childReferences(nodes.get(target)));
            }
        }
        return Optional.empty();
    }
}
