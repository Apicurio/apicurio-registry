import { FunctionComponent, useMemo } from "react";
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
    // Keep a stable PromptTemplate / variables reference across re-renders for a given
    // version content. PromptTemplateTestPanel resets on version identity; an unstable
    // variables object would otherwise be unsafe if that effect ever depended on it.
    const promptTemplate: PromptTemplate = useMemo(() => ({
        templateId: props.spec?.templateId,
        name: props.spec?.name,
        description: props.spec?.description,
        version: props.spec?.version,
        template: props.spec?.template,
        variables: props.spec?.variables,
        outputSchema: props.spec?.outputSchema,
        metadata: props.spec?.metadata,
        mcp: props.spec?.mcp
    }), [
        props.spec?.templateId,
        props.spec?.name,
        props.spec?.description,
        props.spec?.version,
        props.spec?.template,
        props.spec?.variables,
        props.spec?.outputSchema,
        props.spec?.metadata,
        props.spec?.mcp
    ]);

    return (
        <div className={`prompt-template-visualizer ${props.className || ""}`}>
            <PromptTemplateViewer promptTemplate={promptTemplate} />
            <PromptTemplateTestPanel
                groupId={props.groupId}
                artifactId={props.artifactId}
                version={props.version}
                template={promptTemplate.template}
                variables={promptTemplate.variables}
            />
        </div>
    );
};
