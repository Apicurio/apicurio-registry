import { useCallback, useEffect, useState } from "react";
import { Node, Edge } from "@xyflow/react";
import { ReferenceType, ReferenceTypeObject, ReferenceGraphDirectionObject } from "@sdk/lib/generated-client/models";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { LoggerService, useLoggerService } from "@services/useLoggerService.ts";

/**
 * Data stored in each graph node
 */
export interface ReferenceNodeData extends Record<string, unknown> {
    groupId: string;
    artifactId: string;
    version: string;
    name: string;
    isRoot: boolean;
    globalId?: number;
    depth: number;
    isCycleNode?: boolean;
    artifactType?: string | null;
}

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
 * Configuration options for the graph
 */
export interface ReferenceGraphOptions {
    maxDepth?: number;
    referenceType?: ReferenceType;
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

const NODE_WIDTH = 200;
const NODE_HEIGHT = 80;
const HORIZONTAL_SPACING = 80;
const VERTICAL_SPACING = 100;

/**
 * Calculates node positions using a hierarchical layout
 */
const calculateLayout = (
    nodes: Map<string, Node<ReferenceNodeData>>
): void => {
    // Group nodes by depth
    const nodesByDepth = new Map<number, Node<ReferenceNodeData>[]>();
    nodes.forEach((node) => {
        const depth = node.data.depth;
        if (!nodesByDepth.has(depth)) {
            nodesByDepth.set(depth, []);
        }
        nodesByDepth.get(depth)!.push(node);
    });

    // Position nodes at each depth level
    nodesByDepth.forEach((nodesAtDepth, depth) => {
        const totalWidth = nodesAtDepth.length * (NODE_WIDTH + HORIZONTAL_SPACING) - HORIZONTAL_SPACING;
        const startX = -totalWidth / 2;
        const y = depth * (NODE_HEIGHT + VERTICAL_SPACING);

        nodesAtDepth.forEach((node, index) => {
            node.position = {
                x: startX + index * (NODE_WIDTH + HORIZONTAL_SPACING),
                y: y
            };
        });
    });
};

/**
 * Hook for building a reference graph for an artifact using the new backend endpoint
 */
export const useReferenceGraph = (
    coordinates: ArtifactCoordinates | undefined,
    options: ReferenceGraphOptions = {}
): ReferenceGraphResult => {
    const { maxDepth = DEFAULT_MAX_DEPTH, referenceType = ReferenceTypeObject.OUTBOUND } = options;

    const [nodes, setNodes] = useState<Node<ReferenceNodeData>[]>([]);
    const [edges, setEdges] = useState<Edge[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [isError, setIsError] = useState<boolean>(false);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();
    const [hasCycles, setHasCycles] = useState<boolean>(false);
    const [refetchTrigger, setRefetchTrigger] = useState<number>(0);

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const refetch = useCallback(() => {
        setRefetchTrigger((prev) => prev + 1);
    }, []);

    useEffect(() => {
        if (!coordinates || !coordinates.artifactId || !coordinates.version) {
            setNodes([]);
            setEdges([]);
            return;
        }

        const buildGraph = async (): Promise<void> => {
            setIsLoading(true);
            setIsError(false);
            setErrorMessage(undefined);

            try {
                // Map ReferenceType to ReferenceGraphDirection
                const direction = referenceType === ReferenceTypeObject.INBOUND
                    ? ReferenceGraphDirectionObject.INBOUND
                    : ReferenceGraphDirectionObject.OUTBOUND;

                // Fetch the graph from the backend
                const graph = await groups.getArtifactVersionReferencesGraph(
                    coordinates.groupId,
                    coordinates.artifactId,
                    coordinates.version,
                    direction,
                    maxDepth
                );

                // Convert backend response to React Flow nodes and edges
                const nodesMap = new Map<string, Node<ReferenceNodeData>>();
                const edgesList: Edge[] = [];

                // Calculate depth for each node based on edges
                const nodeDepths = new Map<string, number>();
                const rootId = graph.root?.id;
                if (rootId) {
                    nodeDepths.set(rootId, 0);
                }

                // BFS to calculate depths
                if (graph.edges && graph.edges.length > 0) {
                    const queue = [rootId];
                    while (queue.length > 0) {
                        const currentId = queue.shift();
                        if (!currentId) continue;
                        const currentDepth = nodeDepths.get(currentId) || 0;

                        for (const edge of graph.edges) {
                            const sourceId = referenceType === ReferenceTypeObject.OUTBOUND
                                ? edge.sourceNodeId
                                : edge.targetNodeId;
                            const targetId = referenceType === ReferenceTypeObject.OUTBOUND
                                ? edge.targetNodeId
                                : edge.sourceNodeId;

                            if (sourceId === currentId && targetId && !nodeDepths.has(targetId)) {
                                nodeDepths.set(targetId, currentDepth + 1);
                                queue.push(targetId);
                            }
                        }
                    }
                }

                // Process nodes
                if (graph.nodes) {
                    for (const node of graph.nodes) {
                        if (!node.id) continue;

                        const depth = nodeDepths.get(node.id) || 0;
                        nodesMap.set(node.id, {
                            id: node.id,
                            type: "referenceNode",
                            position: { x: 0, y: 0 },
                            data: {
                                groupId: node.groupId || "default",
                                artifactId: node.artifactId || "",
                                version: node.version || "",
                                name: node.name || node.artifactId || "",
                                isRoot: node.isRoot || false,
                                isCycleNode: node.isCycleNode || false,
                                artifactType: node.artifactType,
                                depth: depth
                            }
                        });
                    }
                }

                // Process edges
                if (graph.edges) {
                    for (const edge of graph.edges) {
                        if (!edge.sourceNodeId || !edge.targetNodeId) continue;

                        const edgeId = `${edge.sourceNodeId}->${edge.targetNodeId}`;
                        edgesList.push({
                            id: edgeId,
                            source: edge.sourceNodeId,
                            target: edge.targetNodeId,
                            animated: false,
                            style: { stroke: "#6a6e73" },
                            label: edge.name
                        });
                    }
                }

                // Calculate layout
                calculateLayout(nodesMap);

                // Update state
                setNodes(Array.from(nodesMap.values()));
                setEdges(edgesList);
                setHasCycles(graph.metadata?.hasCycles || false);
            } catch (error: unknown) {
                logger.error("Error fetching reference graph:", error);
                setIsError(true);
                setErrorMessage(error instanceof Error ? error.message : "Failed to load reference graph");
            } finally {
                setIsLoading(false);
            }
        };

        buildGraph();
    }, [coordinates?.groupId, coordinates?.artifactId, coordinates?.version, maxDepth, referenceType, refetchTrigger]);

    return {
        nodes,
        edges,
        isLoading,
        isError,
        errorMessage,
        hasCycles,
        refetch
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
