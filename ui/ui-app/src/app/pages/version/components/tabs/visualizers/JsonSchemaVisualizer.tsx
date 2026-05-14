import { FunctionComponent, useMemo } from "react";
import "./JsonSchemaVisualizer.css";
import { JsonSchemaViewer } from "@app/components/jsonSchema";
import { generateJsonExample } from "@app/components/jsonSchema/generateJsonExample";
import Editor from "@monaco-editor/react";

export type JsonSchemaVisualizerProps = {
    spec: any;
    className?: string;
};

/**
 * Visualizer for JSON Schema content in the documentation tab.
 * Two-panel layout: schema visualization on the left, generated example on the right.
 */
export const JsonSchemaVisualizer: FunctionComponent<JsonSchemaVisualizerProps> = (props: JsonSchemaVisualizerProps) => {
    const exampleJson = useMemo(() => {
        const example = generateJsonExample(props.spec);
        return JSON.stringify(example, null, 4);
    }, [props.spec]);

    return (
        <div className={`json-schema-visualizer ${props.className || ""}`}>
            <div className="json-schema-panel-left">
                <JsonSchemaViewer schema={props.spec} />
            </div>
            <div className="json-schema-panel-right">
                <div className="json-schema-panel-right-header">
                    Generated Example
                </div>
                <div className="json-schema-panel-right-editor">
                    <Editor
                        language="json"
                        value={exampleJson}
                        theme="vs-dark"
                        options={{
                            automaticLayout: true,
                            readOnly: true,
                            minimap: { enabled: false },
                            wordWrap: "on",
                            scrollBeyondLastLine: false,
                            lineNumbers: "on",
                            fontSize: 13,
                            detectIndentation: true
                        }}
                    />
                </div>
            </div>
        </div>
    );
};
