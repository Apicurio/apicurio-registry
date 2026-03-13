import { FunctionComponent } from "react";
import "./PromptTemplateVisualizer.css";
import { PromptTemplateViewer, PromptTemplate } from "@app/components/promptTemplate";
import { PromptTemplateTestPanel } from "@app/components/promptTemplate";

export type PromptTemplateVisualizerProps = {
    spec: any;
    groupId: string;
    artifactId: string;
    version: string;
    className?: string;
};

export const PromptTemplateVisualizer: FunctionComponent<PromptTemplateVisualizerProps> = (props: PromptTemplateVisualizerProps) => {
    const promptTemplate: PromptTemplate = {
        templateId: props.spec?.templateId,
        name: props.spec?.name,
        description: props.spec?.description,
        version: props.spec?.version,
        template: props.spec?.template,
        variables: props.spec?.variables,
        outputSchema: props.spec?.outputSchema,
        metadata: props.spec?.metadata,
        mcp: props.spec?.mcp
    };

    return (
        <div className={`prompt-template-visualizer ${props.className || ""}`}>
            <PromptTemplateViewer promptTemplate={promptTemplate} />
            <PromptTemplateTestPanel
                groupId={props.groupId}
                artifactId={props.artifactId}
                version={props.version}
                variables={promptTemplate.variables}
            />
        </div>
    );
};
