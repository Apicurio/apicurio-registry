import { FunctionComponent, useEffect, useState } from "react";
import { ErrorTabContent } from "@app/pages";
import { If } from "@apicurio/common-ui-components";
import { isJson, parseJson, isYaml, parseYaml } from "@utils/content.utils";
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

/**
 * Parses artifact content string into a structured object for visualization.
 * Returns the parsed object, or undefined if parsing fails or produces a
 * non-object value. Downstream visualizers destructure named properties
 * (e.g. spec.name, spec.template), so primitives are rejected.
 *
 * Built on top of content.utils.ts utilities (isJson, parseJson, isYaml, parseYaml).
 */
const parseContent = (artifactContent: string): any | undefined => {
    if (isJson(artifactContent)) {
        const json = parseJson(artifactContent);
        if (typeof json === "object" && json !== null) return json;
    }
    if (isYaml(artifactContent)) {
        return parseYaml(artifactContent); // isYaml already rejects primitives
    }
    return undefined;
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
            ? (parseContent(props.versionContent) ?? {}) : {}
    );
    const [visualizerType] = useState(getVisualizerType(props.artifactType));
    const [error, setError] = useState<any>();
    const groups: GroupsService = useGroupsService();

    // Handles race condition where versionContent arrives after mount.
    useEffect(() => {
        setError(undefined); // Always clear error when deps change, including dereference types
        if (props.versionContent && !needsDereference(props.artifactType)) {
            setParsedContent(parseContent(props.versionContent) ?? {});
        }
    }, [props.versionContent, props.artifactType]);

    useEffect(() => {
        let cancelled = false;
        setError(undefined); // Clear stale error when dependencies change
        if (needsDereference(props.artifactType) && props.groupId && props.artifactId && props.version) {
            groups.getArtifactVersionContentDereferenced(props.groupId, props.artifactId, props.version)
                .then(content => {
                    // Cancelled check inside callback prevents state updates after unmount
                    if (!cancelled) {
                        setParsedContent(parseContent(content) ?? {});
                    }
                })
                .catch((err) => {
                    if (!cancelled) {
                        // Fall back to raw content if dereference fails
                        if (props.versionContent) {
                            console.warn("[DocumentationTabContent] Dereference failed, falling back to raw content:", err);
                            setParsedContent(parseContent(props.versionContent) ?? {});
                        } else {
                            setError(err);
                        }
                    }
                });
        }
        return () => { cancelled = true; };
    }, [props.artifactType, props.groupId, props.artifactId, props.version, props.versionContent]);

    if (error) {
        return <ErrorTabContent error={{ errorMessage: "Failed to load artifact documentation", error: error }}/>;
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
