import React, { FunctionComponent, memo } from "react";
import { Handle, Position, NodeProps } from "@xyflow/react";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import { ReferenceNodeData } from "@services/useReferenceGraph.ts";
import "./ReferenceGraphNode.css";

/**
 * Custom node component for the reference graph.
 * Displays artifact information in a card-like format.
 */
export const ReferenceGraphNode: FunctionComponent<NodeProps> = memo(({ data, selected }) => {
    const nodeData = data as ReferenceNodeData;
    const displayGroup = nodeData.groupId && nodeData.groupId !== "default" ? nodeData.groupId : null;

    const classNames = [
        "reference-graph-node",
        nodeData.isRoot ? "root" : "",
        nodeData.isCycleNode ? "cycle" : "",
        selected ? "selected" : ""
    ].filter(Boolean).join(" ");

    return (
        <div className={classNames}>
            <Handle type="target" position={Position.Top} className="node-handle" />

            <div className="node-content">
                <div className="node-header">
                    {nodeData.isRoot && <span className="root-badge">ROOT</span>}
                    <span className="artifact-id" title={nodeData.artifactId}>
                        {nodeData.artifactId || nodeData.name}
                    </span>
                </div>

                {displayGroup && (
                    <div className="node-group" title={displayGroup}>
                        {displayGroup}
                    </div>
                )}

                <div className="node-footer">
                    <span className="version-badge">v{nodeData.version}</span>
                    {nodeData.name && nodeData.name !== nodeData.artifactId && (
                        <span className="ref-name" title={nodeData.name}>
                            {nodeData.name}
                        </span>
                    )}
                </div>

                {nodeData.isCycleNode && (
                    <div className="cycle-indicator">
                        <ExclamationTriangleIcon />
                        <span>Circular reference</span>
                    </div>
                )}
            </div>

            <Handle type="source" position={Position.Bottom} className="node-handle" />
        </div>
    );
});

ReferenceGraphNode.displayName = "ReferenceGraphNode";
