import React, { FunctionComponent, useCallback, useState, useRef } from "react";
import {
    ReactFlow,
    Controls,
    MiniMap,
    Background,
    BackgroundVariant,
    useNodesState,
    useEdgesState,
    Node,
    Panel,
    ReactFlowProvider
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
    Button,
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant,
    Slider,
    SliderOnChangeEvent,
    Spinner,
    Alert,
    DescriptionList,
    DescriptionListGroup,
    DescriptionListTerm,
    DescriptionListDescription,
    Tooltip,
    Content
} from "@patternfly/react-core";
import {
    ExpandIcon,
    CompressIcon,
    DownloadIcon,
    InfoCircleIcon
} from "@patternfly/react-icons";
import { useNavigate } from "react-router-dom";
import { ReferenceType, VersionMetaData } from "@sdk/lib/generated-client/models";
import { ReferenceNodeData, useReferenceGraph, ArtifactCoordinates } from "@services/useReferenceGraph.ts";
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
 * Inner component that uses React Flow hooks
 */
const ReferenceGraphInner: FunctionComponent<ReferenceGraphViewProps & {
    maxDepth: number;
    isFullscreen: boolean;
    onToggleFullscreen: () => void;
    selectedNode: Node<ReferenceNodeData> | null;
    onNodeSelect: (node: Node<ReferenceNodeData> | null) => void;
}> = ({
    version,
    referenceType,
    maxDepth,
    isFullscreen,
    onToggleFullscreen,
    selectedNode,
    onNodeSelect
}) => {
    const navigate = useNavigate();
    const reactFlowInstance = useRef<unknown>(null);

    // Build artifact coordinates from version metadata
    const coordinates: ArtifactCoordinates | undefined = version ? {
        groupId: version.groupId || null,
        artifactId: version.artifactId || "",
        version: version.version || ""
    } : undefined;

    // Fetch and build the graph using the new backend endpoint
    const { nodes: initialNodes, edges: initialEdges, isLoading, isError, errorMessage, hasCycles } = useReferenceGraph(
        coordinates,
        { referenceType, maxDepth }
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
        onNodeSelect(node as Node<ReferenceNodeData>);

        // Double click to navigate
        if (event.detail === 2) {
            // Don't navigate for root node or nodes without proper data
            if (data.isRoot || !data.artifactId || !data.version) {
                return;
            }

            const groupId = encodeURIComponent(data.groupId || "default");
            const artifactId = encodeURIComponent(data.artifactId);
            const versionId = encodeURIComponent(data.version);
            // Navigate to the references tab with graph view
            navigate(`/explore/${groupId}/${artifactId}/versions/${versionId}/references?view=graph`);
        }
    }, [navigate, onNodeSelect]);

    // Handle pane click - deselect node
    const onPaneClick = useCallback(() => {
        onNodeSelect(null);
    }, [onNodeSelect]);

    // Memoize minimap node color function
    const minimapNodeColor = useCallback((node: Node) => {
        const data = node.data as ReferenceNodeData;
        if (data.isRoot) {
            return "#0066cc"; // Primary color for root
        }
        if (data.isCycleNode) {
            return "#c9190b"; // Red for cycle nodes
        }
        return "#d2d2d2"; // Default gray for other nodes
    }, []);

    // Export graph as PNG
    const exportAsPng = useCallback(() => {
        if (!reactFlowInstance.current) return;

        // Get the viewport element
        const viewportElement = document.querySelector(".react-flow__viewport") as HTMLElement;
        if (!viewportElement) return;

        // Use html2canvas if available, otherwise use a simple approach
        import("html-to-image").then(({ toPng }) => {
            const flowElement = document.querySelector(".reference-graph-container .react-flow") as HTMLElement;
            if (flowElement) {
                toPng(flowElement, {
                    backgroundColor: "#ffffff",
                    filter: (node) => {
                        // Exclude minimap and controls from export
                        const className = node.className;
                        if (typeof className === "string") {
                            return !className.includes("react-flow__minimap") &&
                                   !className.includes("react-flow__controls") &&
                                   !className.includes("react-flow__panel");
                        }
                        return true;
                    }
                }).then((dataUrl) => {
                    const link = document.createElement("a");
                    link.download = `references-graph-${version?.artifactId || "artifact"}.png`;
                    link.href = dataUrl;
                    link.click();
                }).catch((err) => {
                    console.error("Error exporting graph:", err);
                });
            }
        }).catch(() => {
            // Fallback: alert user to use browser screenshot
            alert("Export not available. Please use your browser's screenshot feature.");
        });
    }, [version]);

    // Loading state
    if (isLoading) {
        return (
            <div className="reference-graph-loading" data-testid="graph-loading">
                <Spinner size="lg" />
                <span>Loading reference graph...</span>
            </div>
        );
    }

    // Error state
    if (isError) {
        return (
            <Alert variant="danger" title="Error loading reference graph" isInline data-testid="graph-error">
                {errorMessage || "An unexpected error occurred while loading the reference graph."}
            </Alert>
        );
    }

    // Empty state - no references
    if (nodes.length <= 1) {
        return (
            <EmptyState titleText="No references found" variant={EmptyStateVariant.xs} data-testid="graph-empty-state">
                <EmptyStateBody>
                    This artifact version has no {referenceType === "OUTBOUND" ? "outbound" : "inbound"} references.
                </EmptyStateBody>
            </EmptyState>
        );
    }

    return (
        <>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onNodeClick={onNodeClick}
                onPaneClick={onPaneClick}
                onInit={(instance) => { reactFlowInstance.current = instance; }}
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

                {/* Top right panel with export and fullscreen buttons */}
                <Panel position="top-right" className="graph-action-panel" data-testid="graph-action-panel">
                    <Tooltip content="Export as PNG">
                        <Button icon={<DownloadIcon />} variant="plain" onClick={exportAsPng} aria-label="Export as PNG" data-testid="graph-export-btn" />
                    </Tooltip>
                    <Tooltip content={isFullscreen ? "Exit fullscreen" : "Fullscreen"}>
                        <Button icon={isFullscreen ? <CompressIcon /> : <ExpandIcon />} variant="plain" onClick={onToggleFullscreen} aria-label="Toggle fullscreen" data-testid="graph-fullscreen-btn" />
                    </Tooltip>
                </Panel>

                {/* Cycle warning */}
                {hasCycles && (
                    <Panel position="top-left" className="graph-warning-panel" data-testid="graph-cycle-warning">
                        <Alert variant="warning" isInline isPlain title="Circular references detected">
                            Some artifacts have circular dependencies (shown with red border).
                        </Alert>
                    </Panel>
                )}
            </ReactFlow>

            {/* Node details panel */}
            {selectedNode && (
                <div className="node-details-panel" data-testid="node-details-panel">
                    <div className="node-details-header">
                        <InfoCircleIcon />
                        <span>Node Details</span>
                        <Button icon="×" variant="plain" onClick={() => onNodeSelect(null)} aria-label="Close" />
                    </div>
                    <DescriptionList isCompact isHorizontal>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Artifact ID</DescriptionListTerm>
                            <DescriptionListDescription>
                                {selectedNode.data.artifactId || "Current Artifact"}
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Group</DescriptionListTerm>
                            <DescriptionListDescription>
                                {selectedNode.data.groupId || "default"}
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Version</DescriptionListTerm>
                            <DescriptionListDescription>
                                {selectedNode.data.version || "-"}
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Reference Name</DescriptionListTerm>
                            <DescriptionListDescription>
                                {selectedNode.data.name || "-"}
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                        {selectedNode.data.isCycleNode && (
                            <DescriptionListGroup>
                                <DescriptionListTerm>Warning</DescriptionListTerm>
                                <DescriptionListDescription className="cycle-warning">
                                    Part of circular reference
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                        )}
                    </DescriptionList>
                    {!selectedNode.data.isRoot && selectedNode.data.artifactId && (
                        <Button
                            variant="link"
                            onClick={() => {
                                const data = selectedNode.data;
                                const groupId = encodeURIComponent(data.groupId || "default");
                                const artifactId = encodeURIComponent(data.artifactId);
                                const versionId = encodeURIComponent(data.version);
                                navigate(`/explore/${groupId}/${artifactId}/versions/${versionId}/references?view=graph`);
                            }}
                        >
                            View this artifact's references
                        </Button>
                    )}
                </div>
            )}

            {/* Legend */}
            <div className="graph-legend" data-testid="graph-legend">
                <div className="legend-item">
                    <span className="legend-color root"></span>
                    <span>Current artifact</span>
                </div>
                <div className="legend-item">
                    <span className="legend-color reference"></span>
                    <span>Referenced artifact</span>
                </div>
                {hasCycles && (
                    <div className="legend-item">
                        <span className="legend-color cycle"></span>
                        <span>Circular reference</span>
                    </div>
                )}
            </div>
        </>
    );
};

/**
 * Component that displays artifact references as an interactive graph.
 */
export const ReferenceGraphView: FunctionComponent<ReferenceGraphViewProps> = ({
    version,
    referenceType
}) => {
    const [maxDepth, setMaxDepth] = useState<number>(3);
    const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
    const [selectedNode, setSelectedNode] = useState<Node<ReferenceNodeData> | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    // Handle depth change
    const onDepthChange = useCallback((
        _event: SliderOnChangeEvent,
        value: number
    ) => {
        setMaxDepth(value);
    }, []);

    // Toggle fullscreen
    const toggleFullscreen = useCallback(() => {
        if (!document.fullscreenElement) {
            containerRef.current?.requestFullscreen();
            setIsFullscreen(true);
        } else {
            document.exitFullscreen();
            setIsFullscreen(false);
        }
    }, []);

    // Listen for fullscreen changes
    React.useEffect(() => {
        const handleFullscreenChange = () => {
            setIsFullscreen(!!document.fullscreenElement);
        };
        document.addEventListener("fullscreenchange", handleFullscreenChange);
        return () => {
            document.removeEventListener("fullscreenchange", handleFullscreenChange);
        };
    }, []);

    return (
        <div className={`reference-graph-wrapper ${isFullscreen ? "fullscreen" : ""}`} ref={containerRef} data-testid="reference-graph-wrapper">
            <Content component="h4">Graph Depth: ({maxDepth})</Content>
            <Content component="small">(Currently shows direct references only. Deeper levels require backend support.)</Content>
            <Slider
                value={maxDepth}
                onChange={onDepthChange}
                min={1}
                max={5}
                step={maxDepth}
                showTicks
                customSteps={[
                    { value: 1, label: "1" },
                    { value: 2, label: "2" },
                    { value: 3, label: "3" },
                    { value: 4, label: "4" },
                    { value: 5, label: "5" }
                ]}
                showBoundaries={false}
            />

            {/* Graph container */}
            <div className="reference-graph-container" data-testid="reference-graph-container" style={{ marginTop: "2rem" }}>
                <ReactFlowProvider>
                    <ReferenceGraphInner
                        version={version}
                        referenceType={referenceType}
                        maxDepth={maxDepth}
                        isFullscreen={isFullscreen}
                        onToggleFullscreen={toggleFullscreen}
                        selectedNode={selectedNode}
                        onNodeSelect={setSelectedNode}
                    />
                </ReactFlowProvider>
            </div>
        </div>
    );
};
