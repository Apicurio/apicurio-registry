import { FunctionComponent } from "react";
import "./ModelSchemaVisualizer.css";
import { ModelSchemaViewer, ModelSchema } from "@app/components/modelSchema";

export type ModelSchemaVisualizerProps = {
    spec: any;
    groupId: string;
    artifactId: string;
    version: string;
    className?: string;
};

export const ModelSchemaVisualizer: FunctionComponent<ModelSchemaVisualizerProps> = (props: ModelSchemaVisualizerProps) => {
    const modelSchema: ModelSchema = {
        modelId: props.spec?.modelId,
        provider: props.spec?.provider,
        version: props.spec?.version,
        description: props.spec?.description,
        input: props.spec?.input,
        output: props.spec?.output,
        metadata: props.spec?.metadata
    };

    return (
        <div className={`model-schema-visualizer ${props.className || ""}`}>
            <ModelSchemaViewer modelSchema={modelSchema} />
        </div>
    );
};
