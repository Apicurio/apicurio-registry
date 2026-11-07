import type { Edge, Node } from "@xyflow/react";
import type { ReferenceGraph, ReferenceGraphDirection } from "@sdk/lib/generated-client/models";

/**
 * Data stored in each graph node.
 */
export interface ReferenceNodeData extends Record<string, unknown> {
    groupId: string;
    artifactId: string;
    version: string;
    name: string;
    isRoot: boolean;
    globalId?: number;
    /**
     * Level relative to the root: positive for referenced artifacts (below the root), negative for
     * referencing artifacts (above the root).
     */
    depth: number;
    isCycleNode?: boolean;
    artifactType?: string | null;
}

/**
 * A reference graph converted to React Flow nodes and edges.
 */
export interface FlowGraph {
    nodes: Node<ReferenceNodeData>[];
    edges: Edge[];
    hasCycles: boolean;
}

const NODE_WIDTH = 200;
const NODE_HEIGHT = 80;
const HORIZONTAL_SPACING = 80;
const VERTICAL_SPACING = 100;

/**
 * Assigns each node a level relative to the root. Edges point from the referencing artifact to the
 * referenced one, so outbound references are reached by following edges forwards (one level down
 * per hop) and inbound references by following them backwards (one level up per hop). Each node
 * keeps the level of the shortest path to it.
 */
const computeLevels = (
    rootId: string,
    edges: ReferenceGraph["edges"],
    direction: ReferenceGraphDirection
): Map<string, number> => {
    const levels = new Map<string, number>([[rootId, 0]]);
    const walk = (forwards: boolean, step: number): void => {
        const queue: string[] = [rootId];
        while (queue.length > 0) {
            const currentId: string = queue.shift()!;
            const currentLevel: number = levels.get(currentId)!;
            for (const e of edges || []) {
                const from = forwards ? e.sourceNodeId : e.targetNodeId;
                const to = forwards ? e.targetNodeId : e.sourceNodeId;
                if (from === currentId && to && !levels.has(to)) {
                    levels.set(to, currentLevel + step);
                    queue.push(to);
                }
            }
        }
    };
    if (direction !== "INBOUND") {
        walk(true, 1);
    }
    if (direction !== "OUTBOUND") {
        walk(false, -1);
    }
    return levels;
};

/**
 * Positions nodes in rows by level, each row centred horizontally.
 */
const layout = (nodes: Node<ReferenceNodeData>[]): Node<ReferenceNodeData>[] => {
    const rows = new Map<number, Node<ReferenceNodeData>[]>();
    nodes.forEach((n) => {
        rows.set(n.data.depth, [...(rows.get(n.data.depth) || []), n]);
    });
    return nodes.map((n) => {
        const row: Node<ReferenceNodeData>[] = rows.get(n.data.depth)!;
        const totalWidth: number = row.length * (NODE_WIDTH + HORIZONTAL_SPACING) - HORIZONTAL_SPACING;
        return {
            ...n,
            position: {
                x: -totalWidth / 2 + row.indexOf(n) * (NODE_WIDTH + HORIZONTAL_SPACING),
                y: n.data.depth * (NODE_HEIGHT + VERTICAL_SPACING)
            }
        };
    });
};

const edgeId = (source: string, target: string, name: string | null | undefined): string =>
    `${source}->${target}:${name ?? ""}`;

/**
 * Converts a reference graph returned by the backend into React Flow nodes and edges, with each
 * node's level relative to the graph root.
 */
export const toFlowGraph = (graph: ReferenceGraph, direction: ReferenceGraphDirection): FlowGraph => {
    const rootId: string | undefined = graph.root?.id ?? undefined;
    const levels: Map<string, number> = rootId ? computeLevels(rootId, graph.edges, direction) : new Map();

    const nodes: Node<ReferenceNodeData>[] = (graph.nodes || [])
        .filter((n) => n.id)
        .map((n) => ({
            id: n.id!,
            type: "referenceNode",
            position: { x: 0, y: 0 },
            data: {
                groupId: n.groupId || "default",
                artifactId: n.artifactId || "",
                version: n.version || "",
                name: n.name || n.artifactId || "",
                isRoot: n.isRoot || false,
                isCycleNode: n.isCycleNode || false,
                artifactType: n.artifactType,
                depth: levels.get(n.id!) ?? 0
            }
        }));

    const edges: Edge[] = [];
    const seen = new Set<string>();
    for (const e of graph.edges || []) {
        if (!e.sourceNodeId || !e.targetNodeId) {
            continue;
        }
        const id: string = edgeId(e.sourceNodeId, e.targetNodeId, e.name);
        if (seen.has(id)) {
            continue;
        }
        seen.add(id);
        edges.push({
            id,
            source: e.sourceNodeId,
            target: e.targetNodeId,
            animated: false,
            style: { stroke: "#6a6e73" },
            label: e.name ?? undefined
        });
    }

    return { nodes: layout(nodes), edges, hasCycles: graph.metadata?.hasCycles || false };
};

/**
 * Returns the direction in which a node's own references extend the graph: nodes above the root
 * were reached through inbound references, all others through outbound references.
 */
export const expansionDirection = (n: Node<ReferenceNodeData>): ReferenceGraphDirection =>
    n.data.depth < 0 ? "INBOUND" : "OUTBOUND";

/**
 * Merges the graph of one expanded node into the current graph. New nodes are placed relative to
 * the expanded node; nodes and edges already in the graph are kept as they are.
 */
export const mergeExpansion = (current: FlowGraph, expandedNodeId: string, expansion: ReferenceGraph): FlowGraph => {
    const expanded: Node<ReferenceNodeData> | undefined = current.nodes.find((n) => n.id === expandedNodeId);
    if (!expanded) {
        return current;
    }
    const sub: FlowGraph = toFlowGraph(expansion, expansionDirection(expanded));
    const knownNodes = new Set<string>(current.nodes.map((n) => n.id));
    const knownEdges = new Set<string>(current.edges.map((e) => e.id));

    const addedNodes: Node<ReferenceNodeData>[] = sub.nodes
        .filter((n) => !knownNodes.has(n.id))
        .map((n) => ({ ...n, data: { ...n.data, isRoot: false, depth: expanded.data.depth + n.data.depth } }));
    const addedEdges: Edge[] = sub.edges.filter((e) => !knownEdges.has(e.id));

    return {
        nodes: layout([...current.nodes, ...addedNodes]),
        edges: [...current.edges, ...addedEdges],
        hasCycles: current.hasCycles || sub.hasCycles
    };
};

/**
 * Whether a node can be expanded on demand. Only nodes at the depth limit can have references the
 * graph does not show yet; with an unlimited depth (0) everything is already shown.
 */
export const isExpandable = (n: Node<ReferenceNodeData>, maxDepth: number, expanded: ReadonlySet<string>): boolean =>
    maxDepth > 0 && !n.data.isRoot && !expanded.has(n.id) && Math.abs(n.data.depth) >= maxDepth;
