import { FunctionComponent, useEffect, useState } from "react";
import { ErrorTabContent } from "@app/pages";
import { If } from "@apicurio/common-ui-components";
import YAML from "yaml";
import {
    AgentCardVisualizer,
    AsyncApiVisualizer,
    JsonSchemaVisualizer,
    McpToolVisualizer,
    ModelSchemaVisualizer,
    OpenApiVisualizer,
    PromptTemplateVisualizer
} from "@app/pages/version/components/tabs/visualizers";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";

enum VisualizerType {
    OPENAPI, ASYNCAPI, AGENT_CARD, MCP_TOOL, JSON_SCHEMA, MODEL_SCHEMA, PROMPT_TEMPLATE, OTHER
}

const getVisualizerType = (artifactType: string): VisualizerType => {
    if (artifactType === ArtifactTypes.OPENAPI) {
        return VisualizerType.OPENAPI;
    }
    if (artifactType === ArtifactTypes.ASYNCAPI) {
        return VisualizerType.ASYNCAPI;
    }
    if (artifactType === ArtifactTypes.AGENT_CARD) {
        return VisualizerType.AGENT_CARD;
    }
    if (artifactType === ArtifactTypes.MCP_TOOL) {
        return VisualizerType.MCP_TOOL;
    }
    if (artifactType === ArtifactTypes.JSON) {
        return VisualizerType.JSON_SCHEMA;
    }
    if (artifactType === ArtifactTypes.MODEL_SCHEMA) {
        return VisualizerType.MODEL_SCHEMA;
    }
    if (artifactType === ArtifactTypes.PROMPT_TEMPLATE) {
        return VisualizerType.PROMPT_TEMPLATE;
    }
    return VisualizerType.OTHER;
};

const parseContent = (artifactContent: string): any => {
    // Try as JSON
    try {
        return JSON.parse(artifactContent);
    } catch {
        // Do nothing
    }

    // Try as YAML
    try {
        return YAML.parse(artifactContent);
    } catch {
        // Do nothing
    }
    return {};
};

/**
 * Properties
 */
export type DocumentationTabContentProps = {
    versionContent: string;
    artifactType: string;
    groupId?: string;
    artifactId?: string;
    version?: string;
};


/**
 * Models the content of the Documentation tab on the artifact details page.
 */
const needsDereference = (artifactType: string): boolean => {
    return artifactType === ArtifactTypes.MODEL_SCHEMA || artifactType === ArtifactTypes.PROMPT_TEMPLATE;
};

export const DocumentationTabContent: FunctionComponent<DocumentationTabContentProps> = (props: DocumentationTabContentProps) => {
    const [parsedContent, setParsedContent] = useState(() =>
        props.versionContent && !needsDereference(props.artifactType)
            ? parseContent(props.versionContent) : {}
    );
    const [visualizerType] = useState(getVisualizerType(props.artifactType));
    const [error] = useState<any>();
    const groups: GroupsService = useGroupsService();

    // Handles race condition where versionContent arrives after mount.
    useEffect(() => {
        if (props.versionContent && !needsDereference(props.artifactType)) {
            setParsedContent(parseContent(props.versionContent));
        }
    }, [props.versionContent, props.artifactType]);

    useEffect(() => {
        if (needsDereference(props.artifactType) && props.groupId && props.artifactId && props.version) {
            groups.getArtifactVersionContentDereferenced(props.groupId, props.artifactId, props.version)
                .then(content => {
                    setParsedContent(parseContent(content));
                })
                .catch(() => {
                    // Fall back to raw content if dereference fails
                });
        }
    }, [props.artifactType, props.groupId, props.artifactId, props.version]);

    const isError = () : boolean => {
        return !!error;
    };

    if (isError()){
        return <ErrorTabContent error={{ errorMessage: "Artifact isn't a valid OpenAPI structure", error: error }}/>;
    }

    return (
        <>
            <If condition={visualizerType === VisualizerType.OPENAPI}>
                <OpenApiVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.ASYNCAPI}>
                <AsyncApiVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.AGENT_CARD}>
                <AgentCardVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.MCP_TOOL}>
                <McpToolVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.JSON_SCHEMA}>
                <JsonSchemaVisualizer spec={parsedContent} />
            </If>
            <If condition={visualizerType === VisualizerType.MODEL_SCHEMA}>
                <ModelSchemaVisualizer
                    spec={parsedContent}
                    groupId={props.groupId || "default"}
                    artifactId={props.artifactId || ""}
                    version={props.version || ""}
                />
            </If>
            <If condition={visualizerType === VisualizerType.PROMPT_TEMPLATE}>
                <PromptTemplateVisualizer
                    spec={parsedContent}
                    groupId={props.groupId || "default"}
                    artifactId={props.artifactId || ""}
                    version={props.version || ""}
                />
            </If>
            <If condition={visualizerType === VisualizerType.OTHER}>
                <h1>Unsupported Type: { props.artifactType }</h1>
            </If>
        </>
    );

};
