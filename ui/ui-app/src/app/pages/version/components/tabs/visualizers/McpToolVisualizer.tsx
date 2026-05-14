import { FunctionComponent } from "react";
import "./McpToolVisualizer.css";
import { McpToolViewer } from "@app/components/mcpTool";

export type McpToolVisualizerProps = {
    spec: any;
    className?: string;
};

/**
 * Visualizer for MCP tool definition content in the documentation tab.
 */
export const McpToolVisualizer: FunctionComponent<McpToolVisualizerProps> = (props: McpToolVisualizerProps) => {
    return (
        <div className={`mcp-tool-visualizer ${props.className || ""}`}>
            <McpToolViewer spec={props.spec} />
        </div>
    );
};
