package io.apicurio.registry.references;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
 * eight separate walkers this replaces used to do (see {@code DESIGN.md} in the project's tracking folder
 * for the inventory of those eight and the bugs that came from their disagreeing).
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
 * replaces did, because changing that is out of scope for #10634 (see {@code DESIGN.md}, "Scope of
 * #10634" — #9626 is what removes the silent-skip behaviour).
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
 * sharing a name is already a bug (#9000 fixes it properly, by keying on coordinates instead of collapsing
 * by name); breadth-first order can pick a different winner than the old depth-first code did in that case,
 * and that is an accepted, deliberate behaviour change (see {@code DESIGN.md}, open question 5) rather than
 * an attempt to preserve what was an arbitrary choice to begin with.</li>
 * <li><b>What order the winning entries appear in the returned map.</b> Dependency-first (post-order): a
 * node appears only after every node it references. This is computed as a second, separate, in-memory-only
 * pass over the already-resolved graph — a depth-first walk from the root's direct references, which costs
 * nothing in I/O because every node it visits is already loaded. It matches the order the walker this
 * replaces produced (open question 4), which at least one content type handler historically depended on.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Immutable and safe to share across threads once built.
 */
public final class ResolvedReferences {

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
     */
    public Map<String, TypedContent> asNameMap() {
        Map<String, GAV> winners = new LinkedHashMap<>();
        for (DiscoveredEdge edge : discoveryOrder) {
            if (!winners.containsKey(edge.name()) && nodes.containsKey(edge.target())) {
                winners.put(edge.name(), edge.target());
            }
        }
        Map<GAV, String> nameByTarget = new HashMap<>();
        for (Map.Entry<String, GAV> entry : winners.entrySet()) {
            nameByTarget.putIfAbsent(entry.getValue(), entry.getKey());
        }
        Map<String, TypedContent> result = new LinkedHashMap<>();
        for (GAV gav : dependencyFirstOrder()) {
            String name = nameByTarget.get(gav);
            if (name != null) {
                result.put(name, nodes.get(gav).getContent());
            }
        }
        return Collections.unmodifiableMap(result);
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
     */
    public List<GAV> dependencyFirstOrder() {
        List<GAV> order = new ArrayList<>(nodes.size());
        Set<GAV> visited = new HashSet<>();
        for (DiscoveredEdge edge : discoveryOrder) {
            visitPostOrder(edge.target(), visited, order);
        }
        return order;
    }

    private void visitPostOrder(GAV gav, Set<GAV> visited, List<GAV> order) {
        if (!visited.add(gav)) {
            return;
        }
        ReferenceNode node = nodes.get(gav);
        if (node == null) {
            // Missing: recorded in missing(), nothing further to visit through it.
            return;
        }
        if (node.getReferences() != null) {
            for (ArtifactReferenceDto reference : node.getReferences()) {
                visitPostOrder(ReferenceGraphWalker.coordinatesOf(reference), visited, order);
            }
        }
        order.add(gav);
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
     */
    public Optional<List<GAV>> findCycle() {
        Map<GAV, Integer> colour = new HashMap<>(); // absent = white, 1 = grey (on the current path), 2 = black (done)
        Deque<GAV> path = new ArrayDeque<>();
        for (GAV start : nodes.keySet()) {
            if (!colour.containsKey(start)) {
                Optional<List<GAV>> cycle = findCycleFrom(start, colour, path);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<List<GAV>> findCycleFrom(GAV gav, Map<GAV, Integer> colour, Deque<GAV> path) {
        colour.put(gav, 1);
        path.addLast(gav);
        ReferenceNode node = nodes.get(gav);
        if (node != null && node.getReferences() != null) {
            for (ArtifactReferenceDto reference : node.getReferences()) {
                GAV target = ReferenceGraphWalker.coordinatesOf(reference);
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
                    Optional<List<GAV>> found = findCycleFrom(target, colour, path);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }
        path.removeLast();
        colour.put(gav, 2);
        return Optional.empty();
    }
}
