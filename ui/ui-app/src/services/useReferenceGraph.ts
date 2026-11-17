import { useCallback, useEffect, useRef, useState } from "react";
import { Node, Edge } from "@xyflow/react";
import { ReferenceGraph, ReferenceGraphDirection } from "@sdk/lib/generated-client/models";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";
import { expansionDirection, FlowGraph, mergeExpansion, ReferenceNodeData, toFlowGraph } from "@utils/referenceGraph.utils.ts";

export type { ReferenceNodeData } from "@utils/referenceGraph.utils.ts";

/**
 * Result of the useReferenceGraph hook
 */
export interface ReferenceGraphResult {
    nodes: Node<ReferenceNodeData>[];
    edges: Edge[];
    isLoading: boolean;
    isError: boolean;
    errorMessage?: string;
    hasCycles: boolean;
    refetch: () => void;
}

/**
 * Result of the useReferenceGraph hook, including on-demand expansion of single nodes.
 */
export interface ExpandableReferenceGraphResult extends ReferenceGraphResult {
    /**
     * Fetches the references of one node, one level deep, and adds them to the graph.
     */
    expandNode: (nodeId: string) => void;
    /**
     * Ids of the nodes that have been expanded since the graph was last loaded.
     */
    expandedNodeIds: ReadonlySet<string>;
    /**
     * Ids of expanded nodes whose expansion added nothing new to the graph.
     */
    exhaustedNodeIds: ReadonlySet<string>;
    /**
     * Id of the node currently being expanded, if any.
     */
    expandingNodeId?: string;
    expandErrorMessage?: string;
}

/**
 * Configuration options for the graph
 */
export interface ReferenceGraphOptions {
    /**
     * Maximum depth to load; 0 loads the whole graph.
     */
    maxDepth?: number;
    direction?: ReferenceGraphDirection;
}

/**
 * Artifact coordinates for graph fetching
 */
export interface ArtifactCoordinates {
    groupId: string | null;
    artifactId: string;
    version: string;
}

const DEFAULT_MAX_DEPTH = 3;

const EMPTY_GRAPH: FlowGraph = { nodes: [], edges: [], hasCycles: false };

/**
 * Hook for building a reference graph for an artifact version from the backend graph endpoint.
 * Nodes at the depth limit can be expanded one level at a time; changing the artifact, the depth
 * or the direction reloads the graph and discards those expansions.
 */
export const useReferenceGraph = (
    coordinates: ArtifactCoordinates | undefined,
    options: ReferenceGraphOptions = {}
): ExpandableReferenceGraphResult => {
    const { maxDepth = DEFAULT_MAX_DEPTH, direction = "OUTBOUND" } = options;

    const [graph, setGraph] = useState<FlowGraph>(EMPTY_GRAPH);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [isError, setIsError] = useState<boolean>(false);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();
    const [refetchTrigger, setRefetchTrigger] = useState<number>(0);
    const [expandedNodeIds, setExpandedNodeIds] = useState<ReadonlySet<string>>(new Set());
    const [exhaustedNodeIds, setExhaustedNodeIds] = useState<ReadonlySet<string>>(new Set());
    const [expandingNodeId, setExpandingNodeId] = useState<string | undefined>();
    const [expandErrorMessage, setExpandErrorMessage] = useState<string | undefined>();
    // Incremented on every reload, so responses for a graph that has since been replaced (a slow
    // load, or an expansion of a node that is no longer shown) are ignored.
    const generation = useRef<number>(0);

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const refetch = useCallback(() => {
        setRefetchTrigger((prev) => prev + 1);
    }, []);

    useEffect(() => {
        const current: number = ++generation.current;
        setExpandedNodeIds(new Set());
        setExhaustedNodeIds(new Set());
        setExpandingNodeId(undefined);
        setExpandErrorMessage(undefined);

        if (!coordinates || !coordinates.artifactId || !coordinates.version) {
            setGraph(EMPTY_GRAPH);
            return;
        }

        setIsLoading(true);
        setIsError(false);
        setErrorMessage(undefined);
        groups.getArtifactVersionReferencesGraph(
            coordinates.groupId, coordinates.artifactId, coordinates.version, direction, maxDepth
        ).then((result: ReferenceGraph) => {
            if (current === generation.current) {
                setGraph(toFlowGraph(result, direction));
            }
        }).catch((error: unknown) => {
            if (current === generation.current) {
                logger.error("Error fetching reference graph:", error);
                setIsError(true);
                setErrorMessage(error instanceof Error ? error.message : "Failed to load reference graph");
            }
        }).finally(() => {
            if (current === generation.current) {
                setIsLoading(false);
            }
        });
    }, [coordinates?.groupId, coordinates?.artifactId, coordinates?.version, maxDepth, direction, refetchTrigger]);

    const expandNode = useCallback((nodeId: string): void => {
        const target: Node<ReferenceNodeData> | undefined = graph.nodes.find((n) => n.id === nodeId);
        if (!target) {
            return;
        }
        const current: number = generation.current;
        setExpandingNodeId(nodeId);
        setExpandErrorMessage(undefined);
        groups.getArtifactVersionReferencesGraph(
            target.data.groupId, target.data.artifactId, target.data.version, expansionDirection(target), 1
        ).then((result: ReferenceGraph) => {
            if (current !== generation.current) {
                return;
            }
            setGraph((prev) => {
                const merged: FlowGraph = mergeExpansion(prev, nodeId, result);
                if (merged.nodes.length === prev.nodes.length && merged.edges.length === prev.edges.length) {
                    setExhaustedNodeIds((ids) => new Set(ids).add(nodeId));
                }
                return merged;
            });
            setExpandedNodeIds((ids) => new Set(ids).add(nodeId));
        }).catch((error: unknown) => {
            if (current === generation.current) {
                logger.error("Error expanding reference graph node:", error);
                setExpandErrorMessage(error instanceof Error ? error.message : "Failed to load references");
            }
        }).finally(() => {
            if (current === generation.current) {
                setExpandingNodeId(undefined);
            }
        });
    }, [graph, groups, logger]);

    return {
        nodes: graph.nodes,
        edges: graph.edges,
        isLoading,
        isError,
        errorMessage,
        hasCycles: graph.hasCycles,
        refetch,
        expandNode,
        expandedNodeIds,
        exhaustedNodeIds,
        expandingNodeId,
        expandErrorMessage
    };
};

/**
 * Legacy hook that uses globalId (for backwards compatibility)
 * @deprecated Use useReferenceGraph with ArtifactCoordinates instead
 */
export const useReferenceGraphByGlobalId = (
    globalId: number | undefined,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _options: ReferenceGraphOptions = {}
): ReferenceGraphResult => {
    // This is a fallback that doesn't use the new endpoint
    // Components should migrate to using coordinates instead
    const [nodes] = useState<Node<ReferenceNodeData>[]>([]);
    const [edges] = useState<Edge[]>([]);
    const [isLoading] = useState<boolean>(false);
    const [isError] = useState<boolean>(false);

    const refetch = useCallback(() => {
        // No-op for legacy
    }, []);

    return {
        nodes,
        edges,
        isLoading,
        isError,
        errorMessage: globalId ? "Please use artifact coordinates instead of globalId" : undefined,
        hasCycles: false,
        refetch
    };
};
