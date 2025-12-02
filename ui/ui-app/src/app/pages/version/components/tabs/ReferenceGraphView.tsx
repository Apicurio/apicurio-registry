import React, { FunctionComponent, useCallback } from "react";
import {
    ReactFlow,
    Controls,
    MiniMap,
    Background,
    BackgroundVariant,
    useNodesState,
    useEdgesState,
    Node
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant,
    Spinner,
    Title,
    Alert
} from "@patternfly/react-core";
import { useNavigate } from "react-router-dom";
import { ReferenceType, VersionMetaData } from "@sdk/lib/generated-client/models";
import { ReferenceNodeData, useReferenceGraph } from "@services/useReferenceGraph.ts";
import { ReferenceGraphNode } from "./ReferenceGraphNode.tsx";
import "./ReferenceGraphView.css";

/**
 * Props for the ReferenceGraphView component
 */
export interface ReferenceGraphViewProps {
    version: VersionMetaData | null;
    referenceType: ReferenceType;
}

/**
 * Custom node types for React Flow
 */
const nodeTypes = {
    referenceNode: ReferenceGraphNode
};

/**
 * Component that displays artifact references as an interactive graph.
 */
export const ReferenceGraphView: FunctionComponent<ReferenceGraphViewProps> = ({
    version,
    referenceType
}) => {
    const navigate = useNavigate();

    // Fetch and build the graph
    const { nodes: initialNodes, edges: initialEdges, isLoading, isError, errorMessage } = useReferenceGraph(
        version?.globalId ?? undefined,
        { referenceType, maxDepth: 3 }
    );

    // Use React Flow's state hooks for interactivity
    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

    // Update nodes and edges when data changes
    React.useEffect(() => {
        setNodes(initialNodes);
        setEdges(initialEdges);
    }, [initialNodes, initialEdges, setNodes, setEdges]);

    // Handle node click - navigate to the artifact's references graph
    const onNodeClick = useCallback((event: React.MouseEvent, node: Node) => {
        const data = node.data as ReferenceNodeData;

        // Don't navigate for root node or nodes without proper data
        if (data.isRoot || !data.artifactId || !data.version) {
            return;
        }

        const groupId = encodeURIComponent(data.groupId || "default");
        const artifactId = encodeURIComponent(data.artifactId);
        const versionId = encodeURIComponent(data.version);
        // Navigate to the references tab with graph view
        navigate(`/explore/${groupId}/${artifactId}/versions/${versionId}/references?view=graph`);
    }, [navigate]);

    // Memoize minimap node color function
    const minimapNodeColor = useCallback((node: Node) => {
        const data = node.data as ReferenceNodeData;
        if (data.isRoot) {
            return "#0066cc"; // Primary color for root
        }
        return "#d2d2d2"; // Default gray for other nodes
    }, []);

    // Loading state
    if (isLoading) {
        return (
            <div className="reference-graph-loading">
                <Spinner size="lg" />
                <span>Loading reference graph...</span>
            </div>
        );
    }

    // Error state
    if (isError) {
        return (
            <Alert variant="danger" title="Error loading reference graph" isInline>
                {errorMessage || "An unexpected error occurred while loading the reference graph."}
            </Alert>
        );
    }

    // Empty state - no references
    if (nodes.length <= 1) {
        return (
            <EmptyState variant={EmptyStateVariant.xs}>
                <Title headingLevel="h4" size="md">No references found</Title>
                <EmptyStateBody>
                    This artifact version has no {referenceType === "OUTBOUND" ? "outbound" : "inbound"} references.
                </EmptyStateBody>
            </EmptyState>
        );
    }

    return (
        <div className="reference-graph-container">
            <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onNodeClick={onNodeClick}
                nodeTypes={nodeTypes}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                minZoom={0.1}
                maxZoom={2}
                attributionPosition="bottom-left"
            >
                <Controls showInteractive={false} />
                <MiniMap
                    nodeColor={minimapNodeColor}
                    maskColor="rgba(0, 0, 0, 0.1)"
                    pannable
                    zoomable
                />
                <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
            </ReactFlow>
            <div className="graph-legend">
                <div className="legend-item">
                    <span className="legend-color root"></span>
                    <span>Current artifact</span>
                </div>
                <div className="legend-item">
                    <span className="legend-color reference"></span>
                    <span>Referenced artifact</span>
                </div>
            </div>
        </div>
    );
};
