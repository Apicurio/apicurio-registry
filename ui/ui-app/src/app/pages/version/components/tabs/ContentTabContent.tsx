import { FunctionComponent, useEffect, useState } from "react";
import "./ContentTabContent.css";
import { ToggleGroup, ToggleGroupItem } from "@patternfly/react-core";
import YAML from "yaml";
import useResizeObserver from "use-resize-observer";
import Editor from "@monaco-editor/react";


const getEditorMode = (artifactType: string): string => {
    if (artifactType === "PROTOBUF") {
        return "protobuf";
    }
    if (artifactType === "WSDL" || artifactType === "XSD" || artifactType === "XML") {
        return "xml";
    }
    if (artifactType === "GRAPHQL") {
        return "graphqlschema";
    }
    return "json";
};

const formatContent = (artifactContent: string): string => {
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
    const [content, setContent] = useState(formatContent(props.versionContent));
    const [editorMode, setEditorMode] = useState(getEditorMode(props.artifactType));
    const [compactButtons, setCompactButtons] = useState(false);

    const { ref, width = 0, height = 0 } = useResizeObserver<HTMLDivElement>();

    useEffect(() => {
        setCompactButtons(width < 500);
    }, [width, height]);

    const switchJsonYaml = (mode: string): (() => void) => {
        return () => {
            if (mode === editorMode) {
                return;
            } else {
                let content: string = `Error formatting code to: ${mode}`;
                try {
                    if (mode === "yaml") {
                        content = YAML.stringify(JSON.parse(props.versionContent), null, 4);
                    } else {
                        content = JSON.stringify(YAML.parse(props.versionContent), null, 2);
                    }
                } catch (e) {
                    handleInvalidContentError(e);
                }
                setEditorMode(mode);
                setContent(content);
            }
        };
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
                        onChange={switchJsonYaml("json")}
                        isDisabled={editorMode === "json"}
                    />
                    <ToggleGroupItem
                        text="YAML"
                        buttonId="yaml"
                        isSelected={editorMode === "yaml"}
                        onChange={switchJsonYaml("yaml")}
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

