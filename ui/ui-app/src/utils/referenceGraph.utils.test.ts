import { describe, expect, it } from "vitest";
import type { ReferenceGraph, ReferenceGraphEdge, ReferenceGraphNode } from "@sdk/lib/generated-client/models";
import { FlowGraph, isExpandable, mergeExpansion, toFlowGraph } from "./referenceGraph.utils.ts";

const node = (id: string, extra: Partial<ReferenceGraphNode> = {}): ReferenceGraphNode =>
    ({ id, groupId: "g", artifactId: id, version: "1", ...extra }) as ReferenceGraphNode;

/** An edge always points from the referencing artifact to the referenced one. */
const edge = (source: string, target: string, name: string = `${target}.json`): ReferenceGraphEdge =>
    ({ sourceNodeId: source, targetNodeId: target, name }) as ReferenceGraphEdge;

const graph = (root: string, nodes: ReferenceGraphNode[], edges: ReferenceGraphEdge[], hasCycles = false): ReferenceGraph =>
    ({
        root: { id: root },
        nodes: [node(root, { isRoot: true }), ...nodes],
        edges,
        metadata: { hasCycles }
    }) as ReferenceGraph;

const levels = (flow: FlowGraph): Record<string, number> =>
    Object.fromEntries(flow.nodes.map((n) => [n.id, n.data.depth]));

describe("toFlowGraph", () => {
    it("places outbound references below the root, one level per hop", () => {
        const flow: FlowGraph = toFlowGraph(
            graph("root", [node("a"), node("b")], [edge("root", "a"), edge("a", "b")]),
            "OUTBOUND"
        );

        expect(levels(flow)).toEqual({ root: 0, a: 1, b: 2 });
    });

    it("places inbound references above the root", () => {
        const flow: FlowGraph = toFlowGraph(
            graph("root", [node("x"), node("y")], [edge("x", "root"), edge("y", "x")]),
            "INBOUND"
        );

        expect(levels(flow)).toEqual({ root: 0, x: -1, y: -2 });
    });

    it("places referencing artifacts above and referenced artifacts below for BOTH", () => {
        const flow: FlowGraph = toFlowGraph(
            graph(
                "root",
                [node("a"), node("b"), node("x"), node("y")],
                [edge("root", "a"), edge("a", "b"), edge("x", "root"), edge("y", "x")]
            ),
            "BOTH"
        );

        expect(levels(flow)).toEqual({ root: 0, a: 1, b: 2, x: -1, y: -2 });
    });

    it("keeps the shortest level and terminates when references form a cycle", () => {
        const flow: FlowGraph = toFlowGraph(
            graph("root", [node("a", { isCycleNode: true })], [edge("root", "a"), edge("a", "root")], true),
            "OUTBOUND"
        );

        expect(levels(flow)).toEqual({ root: 0, a: 1 });
        expect(flow.hasCycles).toBe(true);
        expect(flow.nodes.find((n) => n.id === "a")?.data.isCycleNode).toBe(true);
    });

    it("keeps one edge per distinct reference name between the same two nodes", () => {
        const flow: FlowGraph = toFlowGraph(
            graph("root", [node("a")], [edge("root", "a", "one.json"), edge("root", "a", "two.json")]),
            "OUTBOUND"
        );

        expect(flow.edges.map((e) => e.label)).toEqual(["one.json", "two.json"]);
        expect(new Set(flow.edges.map((e) => e.id)).size).toBe(2);
    });

    it("lays out nodes on the same level side by side and levels top to bottom", () => {
        const flow: FlowGraph = toFlowGraph(
            graph("root", [node("a"), node("b"), node("x")], [edge("root", "a"), edge("root", "b"), edge("x", "root")]),
            "BOTH"
        );
        const pos = Object.fromEntries(flow.nodes.map((n) => [n.id, n.position]));

        expect(pos.a.y).toBe(pos.b.y);
        expect(pos.a.x).not.toBe(pos.b.x);
        expect(pos.x.y).toBeLessThan(pos.root.y);
        expect(pos.root.y).toBeLessThan(pos.a.y);
    });
});

describe("mergeExpansion", () => {
    const base: FlowGraph = toFlowGraph(
        graph("root", [node("a"), node("b")], [edge("root", "a"), edge("root", "b")]),
        "OUTBOUND"
    );

    it("adds the expanded node's references one level below it", () => {
        const merged: FlowGraph = mergeExpansion(base, "a", graph("a", [node("c")], [edge("a", "c")]));

        expect(levels(merged)).toEqual({ root: 0, a: 1, b: 1, c: 2 });
        expect(merged.edges.map((e) => `${e.source}->${e.target}`)).toEqual(["root->a", "root->b", "a->c"]);
    });

    it("adds an inbound expansion one level above the expanded node", () => {
        const inbound: FlowGraph = toFlowGraph(graph("root", [node("x")], [edge("x", "root")]), "INBOUND");

        const merged: FlowGraph = mergeExpansion(inbound, "x", graph("x", [node("y")], [edge("y", "x")]));

        expect(levels(merged)).toEqual({ root: 0, x: -1, y: -2 });
    });

    it("does not duplicate nodes or edges that are already in the graph", () => {
        const merged: FlowGraph = mergeExpansion(base, "a", graph("a", [node("b")], [edge("a", "b"), edge("root", "a")]));

        expect(merged.nodes.map((n) => n.id)).toEqual(["root", "a", "b"]);
        expect(levels(merged)).toEqual({ root: 0, a: 1, b: 1 });
        expect(merged.edges.map((e) => `${e.source}->${e.target}`)).toEqual(["root->a", "root->b", "a->b"]);
    });

    it("keeps the original root as the only root", () => {
        const merged: FlowGraph = mergeExpansion(base, "a", graph("a", [node("c")], [edge("a", "c")]));

        expect(merged.nodes.filter((n) => n.data.isRoot).map((n) => n.id)).toEqual(["root"]);
    });

    it("reports cycles found by the expansion", () => {
        const merged: FlowGraph = mergeExpansion(
            base, "a", graph("a", [node("root", { isCycleNode: true })], [edge("a", "root")], true)
        );

        expect(merged.hasCycles).toBe(true);
    });
});

describe("isExpandable", () => {
    const flow: FlowGraph = toFlowGraph(
        graph("root", [node("a"), node("b"), node("x")], [edge("root", "a"), edge("a", "b"), edge("x", "root")]),
        "BOTH"
    );
    const byId = (id: string) => flow.nodes.find((n) => n.id === id)!;

    it("is true only for nodes at the depth limit", () => {
        expect(isExpandable(byId("b"), 2, new Set())).toBe(true);
        expect(isExpandable(byId("a"), 2, new Set())).toBe(false);
    });

    it("counts levels above the root towards the limit", () => {
        expect(isExpandable(byId("x"), 1, new Set())).toBe(true);
    });

    it("is false for the root, for expanded nodes, and when the depth is unlimited", () => {
        expect(isExpandable(byId("root"), 0, new Set())).toBe(false);
        expect(isExpandable(byId("b"), 2, new Set(["b"]))).toBe(false);
        expect(isExpandable(byId("b"), 0, new Set())).toBe(false);
    });
});
