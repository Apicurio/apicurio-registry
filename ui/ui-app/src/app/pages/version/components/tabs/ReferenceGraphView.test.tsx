// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import type { ReferenceGraph, ReferenceGraphEdge, ReferenceGraphNode, VersionMetaData } from "@sdk/lib/generated-client/models";
import { ReferenceGraphView } from "./ReferenceGraphView";

const getArtifactVersionReferencesGraph = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({ getArtifactVersionReferencesGraph })
}));
vi.mock("@services/useLoggerService.ts", () => ({
    useLoggerService: () => ({ error: vi.fn() })
}));
vi.mock("react-router", () => ({
    useNavigate: () => vi.fn()
}));
vi.mock("./ReferenceGraphNode.tsx", () => ({ ReferenceGraphNode: () => null }));
// React Flow needs layout APIs jsdom does not have; a stand-in renders each node as a button so the
// tests can select nodes and see which ones are in the graph.
vi.mock("@xyflow/react", () => {
    type FlowNode = { id: string };
    const passthrough = ({ children }: { children?: unknown }) => <>{children}</>;
    return {
        ReactFlow: (props: { nodes: FlowNode[]; onNodeClick: (e: unknown, n: FlowNode) => void; children?: unknown }) => (
            <div>
                {props.nodes.map((n) => (
                    <button key={n.id} data-testid={`node-${n.id}`} onClick={() => props.onNodeClick({ detail: 1 }, n)} />
                ))}
                {props.children as never}
            </div>
        ),
        ReactFlowProvider: passthrough,
        Panel: passthrough,
        Controls: () => null,
        MiniMap: () => null,
        Background: () => null,
        BackgroundVariant: { Dots: "dots" },
        useNodesState: (initial: unknown[]) => {
            const [nodes, setNodes] = useState(initial);
            return [nodes, setNodes, vi.fn()];
        },
        useEdgesState: (initial: unknown[]) => {
            const [edges, setEdges] = useState(initial);
            return [edges, setEdges, vi.fn()];
        }
    };
});

const node = (id: string, isRoot = false): ReferenceGraphNode =>
    ({ id, groupId: "g", artifactId: id, version: "1", isRoot }) as ReferenceGraphNode;

const edge = (source: string, target: string): ReferenceGraphEdge =>
    ({ sourceNodeId: source, targetNodeId: target, name: `${target}.json` }) as ReferenceGraphEdge;

const graph = (root: string, nodes: string[], edges: ReferenceGraphEdge[]): ReferenceGraph =>
    ({ root: { id: root }, nodes: [node(root, true), ...nodes.map((n) => node(n))], edges, metadata: {} }) as ReferenceGraph;

/** root -> a -> b -> c: with the default depth of 3, c sits at the limit. */
const CHAIN: ReferenceGraph = graph("root", ["a", "b", "c"], [edge("root", "a"), edge("a", "b"), edge("b", "c")]);

const VERSION = { groupId: "g", artifactId: "root", version: "1" } as VersionMetaData;

/** Lets pending promise callbacks run and React apply the resulting state updates. */
const flush = async (): Promise<void> => {
    await act(async () => {
        await new Promise((r) => setTimeout(r, 0));
    });
};

const renderView = async (referenceType: "OUTBOUND" | "INBOUND" = "OUTBOUND"): Promise<void> => {
    render(<ReferenceGraphView version={VERSION} referenceType={referenceType} />);
    await flush();
};

const select = (id: string): void => {
    fireEvent.click(screen.getByTestId(`node-${id}`));
};

const expandButton = (): HTMLElement | null => screen.queryByRole("button", { name: "Expand references" });

beforeEach(() => {
    getArtifactVersionReferencesGraph.mockResolvedValue(CHAIN);
});

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ReferenceGraphView", () => {
    it("loads the graph to the default depth in the tab's direction", async () => {
        await renderView("INBOUND");

        expect(getArtifactVersionReferencesGraph).toHaveBeenCalledWith("g", "root", "1", "INBOUND", 3);
        expect(screen.queryByText(/direct references only/)).not.toBeInTheDocument();
    });

    it("offers to expand only nodes at the depth limit", async () => {
        await renderView();

        select("b");
        expect(expandButton()).not.toBeInTheDocument();
        select("c");
        expect(expandButton()).toBeInTheDocument();
    });

    it("fetches one more level for the expanded node and adds its references", async () => {
        await renderView();
        getArtifactVersionReferencesGraph.mockResolvedValueOnce(graph("c", ["d"], [edge("c", "d")]));

        select("c");
        fireEvent.click(expandButton()!);
        await flush();

        expect(getArtifactVersionReferencesGraph).toHaveBeenLastCalledWith("g", "c", "1", "OUTBOUND", 1);
        expect(screen.getByTestId("node-d")).toBeInTheDocument();
        expect(expandButton()).not.toBeInTheDocument();
        select("d");
        expect(expandButton()).toBeInTheDocument();
    });

    it("says so when an expanded node has no further references", async () => {
        await renderView();
        getArtifactVersionReferencesGraph.mockResolvedValueOnce(graph("c", [], []));

        select("c");
        fireEvent.click(expandButton()!);
        await flush();

        expect(screen.getByText("No further references.")).toBeInTheDocument();
    });

    it("clears expansions when the depth changes", async () => {
        await renderView();
        getArtifactVersionReferencesGraph.mockResolvedValueOnce(graph("c", ["d"], [edge("c", "d")]));
        select("c");
        fireEvent.click(expandButton()!);
        await flush();
        expect(screen.getByTestId("node-d")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "2" }));
        await flush();

        expect(getArtifactVersionReferencesGraph).toHaveBeenLastCalledWith("g", "root", "1", "OUTBOUND", 2);
        expect(screen.queryByTestId("node-d")).not.toBeInTheDocument();
    });

    it("requests an unlimited depth for All and offers no expansion", async () => {
        await renderView();

        fireEvent.click(screen.getByRole("button", { name: "All" }));
        await flush();
        select("c");

        expect(getArtifactVersionReferencesGraph).toHaveBeenLastCalledWith("g", "root", "1", "OUTBOUND", 0);
        expect(expandButton()).not.toBeInTheDocument();
    });

    it("requests both directions when Both is chosen", async () => {
        await renderView();

        fireEvent.click(screen.getByRole("button", { name: "Both" }));
        await flush();

        expect(getArtifactVersionReferencesGraph).toHaveBeenLastCalledWith("g", "root", "1", "BOTH", 3);
    });

    it("expands a referencing artifact in the inbound direction", async () => {
        // x -> root: in the Both view x sits one level above the root.
        getArtifactVersionReferencesGraph.mockResolvedValue(graph("root", ["x"], [edge("x", "root")]));
        await renderView();
        fireEvent.click(screen.getByRole("button", { name: "Both" }));
        fireEvent.click(screen.getByRole("button", { name: "1" }));
        await flush();
        getArtifactVersionReferencesGraph.mockResolvedValueOnce(graph("x", ["y"], [edge("y", "x")]));

        select("x");
        fireEvent.click(expandButton()!);
        await flush();

        expect(getArtifactVersionReferencesGraph).toHaveBeenLastCalledWith("g", "x", "1", "INBOUND", 1);
        expect(screen.getByTestId("node-y")).toBeInTheDocument();
    });
});
