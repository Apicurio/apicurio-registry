import { FunctionComponent } from "react";
import "./JsonSchemaVisualizer.css";
import { JsonSchemaViewer } from "@app/components/jsonSchema";

export type JsonSchemaVisualizerProps = {
    spec: any;
    className?: string;
};

/**
 * Visualizer for JSON Schema content in the documentation tab.
 */
export const JsonSchemaVisualizer: FunctionComponent<JsonSchemaVisualizerProps> = (props: JsonSchemaVisualizerProps) => {
    return (
        <div className={`json-schema-visualizer ${props.className || ""}`}>
            <JsonSchemaViewer schema={props.spec} />
        </div>
    );
};
