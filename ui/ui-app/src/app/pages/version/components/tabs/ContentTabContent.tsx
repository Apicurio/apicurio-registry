import { FunctionComponent, useEffect, useState } from "react";
import "./ContentTabContent.css";
import { ToggleGroup, ToggleGroupItem } from "@patternfly/react-core";
import YAML from "yaml";
import useResizeObserver from "use-resize-observer";
import Editor from "@monaco-editor/react";
import { detectContentType } from "@utils/content.utils.ts";
import { ContentTypes } from "@models/contentTypes.model.ts";

const TYPE_MAP: any = {};
TYPE_MAP[ContentTypes.APPLICATION_PROTOBUF] = "protobuf";
TYPE_MAP[ContentTypes.APPLICATION_XML] = "xml";
TYPE_MAP[ContentTypes.APPLICATION_JSON] = "json";
TYPE_MAP[ContentTypes.APPLICATION_YAML] = "yaml";
TYPE_MAP[ContentTypes.APPLICATION_GRAPHQL] = "graphqlschema";


const getEditorMode = (artifactType: string, content: string): string => {
    const ct: string = detectContentType(artifactType, content);
    return TYPE_MAP[ct];
};

const formatJsonContent = (artifactContent: string): string => {
    try {
        const pval: any = JSON.parse(artifactContent);
        if (pval) {
            return JSON.stringify(pval, null, 2);
        }
    } catch (e) {
        // Do nothing
    }
    return artifactContent;
};


/**
 * Properties
 */
export type ContentTabContentProps = {
    versionContent: string;
    artifactType: string;
};


/**
 * Models the content of the Artifact Content tab.
 */
export const ContentTabContent: FunctionComponent<ContentTabContentProps> = (props: ContentTabContentProps) => {
    const em: string = getEditorMode(props.artifactType, props.versionContent);
    const fc: string = em === "json" ? formatJsonContent(props.versionContent) : props.versionContent;

    const [content, setContent] = useState(fc);
    const [editorMode, setEditorMode] = useState(em);
    const [compactButtons, setCompactButtons] = useState(false);

    const { ref, width = 0, height = 0 } = useResizeObserver<HTMLDivElement>();

    useEffect(() => {
        setCompactButtons(width < 500);
    }, [width, height]);

    const switchJsonYaml = (mode: string): void => {
        console.info("SWITCHING TO: ", mode);
        if (mode === editorMode) {
            return;
        } else {
            let newContent: string = `Error formatting code to: ${mode}`;
            try {
                if (mode === "yaml") {
                    newContent = YAML.stringify(JSON.parse(content), null, 4);
                    console.info("NEW CONTENT (yaml): ", newContent);
                } else {
                    newContent = JSON.stringify(YAML.parse(content), null, 2);
                    console.info("NEW CONTENT (json): ", newContent);
                }
            } catch (e) {
                handleInvalidContentError(e);
            }
            setEditorMode(mode);
            setContent(newContent);
        }
    };

    const handleInvalidContentError = (error: any): void => {
        console.info("[Content] Invalid content error:", error);
    };

    return (
        <div className="code-wrapper" id="code-wrapper" ref={ref}>
            { !(editorMode === "json" || editorMode === "yaml") ? null :
                <ToggleGroup aria-label="Switch Json to Yaml" isCompact={compactButtons} className="formatting-buttons">
                    <ToggleGroupItem
                        text="JSON"
                        buttonId="json"
                        isSelected={editorMode === "json"}
                        onChange={() => switchJsonYaml("json")}
                        isDisabled={editorMode === "json"}
                    />
                    <ToggleGroupItem
                        text="YAML"
                        buttonId="yaml"
                        isSelected={editorMode === "yaml"}
                        onChange={() => switchJsonYaml("yaml")}
                        isDisabled={editorMode === "yaml"}
                    />
                </ToggleGroup>
            }

            <Editor
                className="text-editor"
                language={editorMode}
                value={content}
                theme="vs-dark"
                options={{
                    automaticLayout: true,
                    minimap: {
                        enabled: false
                    },
                    wordWrap: "on"
                }}
                onMount={(editor: any) => {
                    editor.layout();
                }}
            />
        </div>
    );

};

