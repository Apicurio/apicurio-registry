import { FunctionComponent } from "react";
import "./McpToolVisualizer.css";
import { McpToolViewer, CompatibleMcpToolsViewer } from "@app/components/mcpTool";

export type McpToolVisualizerProps = {
    spec: any;
    groupId?: string;
    artifactId?: string;
    version?: string;
    className?: string;
};

/**
 * Visualizer for MCP tool definition content in the documentation tab.
 */
export const McpToolVisualizer: FunctionComponent<McpToolVisualizerProps> = (props: McpToolVisualizerProps) => {
    return (
        <div className={`mcp-tool-visualizer ${props.className || ""}`}>
            <McpToolViewer spec={props.spec} />
            {props.groupId && props.artifactId && (
                <CompatibleMcpToolsViewer
                    groupId={props.groupId}
                    artifactId={props.artifactId}
                    version={props.version}
                />
            )}
        </div>
    );
};

