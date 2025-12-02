import { useCallback, useEffect, useState } from "react";
import { Node, Edge } from "@xyflow/react";
import { ArtifactReference, ReferenceType, ReferenceTypeObject } from "@sdk/lib/generated-client/models";
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
    refetch: () => void;
}

/**
 * Configuration options for the graph
 */
export interface ReferenceGraphOptions {
    maxDepth?: number;
    referenceType?: ReferenceType;
}

const DEFAULT_MAX_DEPTH = 3;

/**
 * Creates a unique node ID from artifact coordinates
 */
const createNodeId = (groupId: string | null | undefined, artifactId: string, version: string): string => {
    const g = groupId || "default";
    return `${g}/${artifactId}/${version}`;
};

/**
 * Creates a unique edge ID
 */
const createEdgeId = (sourceId: string, targetId: string): string => {
    return `${sourceId}->${targetId}`;
};

/**
 * Calculates node positions using a hierarchical layout
 */
const calculateLayout = (
    nodes: Map<string, Node<ReferenceNodeData>>
): void => {
    const NODE_WIDTH = 200;
    const NODE_HEIGHT = 80;
    const HORIZONTAL_SPACING = 80;
    const VERTICAL_SPACING = 100;

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
 * Hook for building a reference graph for an artifact
 */
export const useReferenceGraph = (
    globalId: number | undefined,
    options: ReferenceGraphOptions = {}
): ReferenceGraphResult => {
    const { maxDepth = DEFAULT_MAX_DEPTH, referenceType = ReferenceTypeObject.OUTBOUND } = options;

    const [nodes, setNodes] = useState<Node<ReferenceNodeData>[]>([]);
    const [edges, setEdges] = useState<Edge[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [isError, setIsError] = useState<boolean>(false);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();
    const [refetchTrigger, setRefetchTrigger] = useState<number>(0);

    const groups: GroupsService = useGroupsService();
    const logger: LoggerService = useLoggerService();

    const refetch = useCallback(() => {
        setRefetchTrigger((prev) => prev + 1);
    }, []);

    useEffect(() => {
        if (!globalId) {
            setNodes([]);
            setEdges([]);
            return;
        }

        const nodesMap = new Map<string, Node<ReferenceNodeData>>();
        const edgesMap = new Map<string, Edge>();
        const visited = new Set<string>();

        const fetchReferencesRecursively = async (
            currentGlobalId: number,
            parentNodeId: string | null,
            referenceName: string | null,
            depth: number
        ): Promise<void> => {
            if (depth > maxDepth) {
                return;
            }

            try {
                const refs: ArtifactReference[] = await groups.getArtifactReferences(currentGlobalId, referenceType);

                for (const ref of refs) {
                    const nodeId = createNodeId(ref.groupId, ref.artifactId!, ref.version!);

                    // Add edge from parent to this node (or from this node to parent for inbound)
                    if (parentNodeId) {
                        const edgeId = referenceType === ReferenceTypeObject.OUTBOUND
                            ? createEdgeId(parentNodeId, nodeId)
                            : createEdgeId(nodeId, parentNodeId);

                        if (!edgesMap.has(edgeId)) {
                            edgesMap.set(edgeId, {
                                id: edgeId,
                                source: referenceType === ReferenceTypeObject.OUTBOUND ? parentNodeId : nodeId,
                                target: referenceType === ReferenceTypeObject.OUTBOUND ? nodeId : parentNodeId,
                                animated: false,
                                style: { stroke: "#6a6e73" }
                            });
                        }
                    }

                    // Skip if already visited (cycle detection)
                    if (visited.has(nodeId)) {
                        continue;
                    }
                    visited.add(nodeId);

                    // Add node
                    if (!nodesMap.has(nodeId)) {
                        nodesMap.set(nodeId, {
                            id: nodeId,
                            type: "referenceNode",
                            position: { x: 0, y: 0 }, // Will be calculated later
                            data: {
                                groupId: ref.groupId || "default",
                                artifactId: ref.artifactId!,
                                version: ref.version!,
                                name: ref.name || ref.artifactId!,
                                isRoot: false,
                                globalId: undefined, // We don't have globalId for referenced artifacts
                                depth: depth
                            }
                        });
                    }

                    // Note: We can't recursively fetch here because we don't have the globalId
                    // of the referenced artifacts. This would require an additional API call
                    // to get the version metadata first. For MVP, we only show direct references.
                }
            } catch (error) {
                logger.error(`Error fetching references for globalId ${currentGlobalId}:`, error);
                throw error;
            }
        };

        const buildGraph = async (): Promise<void> => {
            setIsLoading(true);
            setIsError(false);
            setErrorMessage(undefined);

            try {
                // Create root node (we need to fetch its metadata to get artifact info)
                // For now, we'll create a placeholder that will be updated
                const rootNodeId = `root-${globalId}`;
                nodesMap.set(rootNodeId, {
                    id: rootNodeId,
                    type: "referenceNode",
                    position: { x: 0, y: 0 },
                    data: {
                        groupId: "",
                        artifactId: "",
                        version: "",
                        name: "Current Artifact",
                        isRoot: true,
                        globalId: globalId,
                        depth: 0
                    }
                });
                visited.add(rootNodeId);

                // Fetch direct references
                await fetchReferencesRecursively(globalId, rootNodeId, null, 1);

                // Calculate layout
                calculateLayout(nodesMap);

                // Update state
                setNodes(Array.from(nodesMap.values()));
                setEdges(Array.from(edgesMap.values()));
            } catch (error: any) {
                logger.error("Error building reference graph:", error);
                setIsError(true);
                setErrorMessage(error?.message || "Failed to load reference graph");
            } finally {
                setIsLoading(false);
            }
        };

        buildGraph();
    }, [globalId, maxDepth, referenceType, refetchTrigger]);

    return {
        nodes,
        edges,
        isLoading,
        isError,
        errorMessage,
        refetch
    };
};
