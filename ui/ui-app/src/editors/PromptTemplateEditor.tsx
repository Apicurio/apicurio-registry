import { useEffect, useMemo, useRef, useState } from "react";
import "./PromptTemplateEditor.css";
import type { editor } from "monaco-editor";
import type { Editor as DraftEditor, EditorProps } from "./editor-types";
import { Alert, AlertActionCloseButton, Button, Label } from "@patternfly/react-core";
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import { draftContentToString } from "@utils/content.utils.ts";
import { PROMPT_TEMPLATE_LANGUAGE_ID } from "./PromptTemplateLanguage.ts";
import {
    detectPromptVariables,
    syncPromptTemplateVariables,
    VariableDetectionResult
} from "./promptTemplateSync";

type IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

export const PROMPT_TEMPLATE_EDITOR_ARIA_LABEL = "Prompt template source editor";

/** The first line of an error, without the source excerpt the YAML parser appends after a colon. */
const errorSummary = (error: string | undefined): string => (error ?? "").split("\n")[0].replace(/:$/, "");

export const PromptTemplateEditor: DraftEditor = (props: EditorProps) => {
    const editorRef = useRef<IStandaloneCodeEditor | undefined>(undefined);
    const [currentText, setCurrentText] = useState<string>(() => draftContentToString(props.content));
    const [syncError, setSyncError] = useState<string | undefined>(undefined);
    const contentType = props.content.contentType;

    useEffect(() => {
        const model = editorRef.current?.getModel();
        const contentString = draftContentToString(props.content);
        // Avoid resetting user cursor and undo history when parent re-renders with unchanged content.
        if (!model || model.getValue() === contentString) {
            return;
        }

        // Apply external updates (e.g. draft recovery) as one undoable edit; switching drafts remounts instead.
        model.pushStackElement();
        model.pushEditOperations([], [{ range: model.getFullModelRange(), text: contentString }], () => null);
        model.pushStackElement();
    }, [props.content]);

    const detection: VariableDetectionResult = useMemo(() => {
        return detectPromptVariables(currentText, contentType);
    }, [currentText, contentType]);

    const handleEditorChange = (newValue: string | undefined): void => {
        const val = newValue ?? "";
        setCurrentText(val);
        setSyncError(undefined);
        props.onChange(val);
    };

    const handleSync = (): void => {
        const model = editorRef.current?.getModel();
        const activeText = model ? model.getValue() : currentText;
        const result = syncPromptTemplateVariables(activeText, contentType);

        if (!result.success) {
            setSyncError(result.error || "Failed to synchronize variables.");
            return;
        }

        setSyncError(undefined);

        if (!result.insertion) {
            return;
        }

        if (model) {
            // Insert only the new entries, as one undoable edit; the model change reaches handleEditorChange.
            const at = model.getPositionAt(result.insertion.offset);
            const range = {
                startLineNumber: at.lineNumber,
                startColumn: at.column,
                endLineNumber: at.lineNumber,
                endColumn: at.column
            };
            model.pushStackElement();
            model.pushEditOperations([], [{ range, text: result.insertion.text }], () => null);
            model.pushStackElement();
            setCurrentText(result.updatedSource);
            return;
        }

        setCurrentText(result.updatedSource);
        props.onChange(result.updatedSource);
    };

    const syncDisabled = !detection.valid || !detection.templateFound || detection.missingCount === 0;

    return (
        <div className="prompt-template-editor">
            <div className="prompt-template-editor-toolbar" data-testid="prompt-template-editor-toolbar">
                <div className="prompt-template-editor-status">
                    <span className="status-title">Variables:</span>
                    {!detection.valid ? (
                        <>
                            <Label color="red" isCompact data-testid="badge-malformed">
                                Invalid document
                            </Label>
                            <span className="status-error" data-testid="detection-error">
                                {errorSummary(detection.error)}
                            </span>
                        </>
                    ) : !detection.templateFound ? (
                        <Label color="grey" isCompact data-testid="badge-no-template">
                            No template field
                        </Label>
                    ) : (
                        <>
                            <Label color="green" isCompact data-testid="badge-synced">
                                {detection.synchronizedCount} synced
                            </Label>
                            <Label
                                color={detection.missingCount > 0 ? "orange" : "grey"}
                                isCompact
                                data-testid="badge-missing"
                            >
                                {detection.missingCount} missing
                            </Label>
                            <Label color="grey" isCompact data-testid="badge-unused">
                                {detection.unusedCount} unused
                            </Label>
                            {detection.conditionalCount > 0 && (
                                <Label color="blue" isCompact data-testid="badge-conditional">
                                    {detection.conditionalCount} conditional
                                </Label>
                            )}
                        </>
                    )}
                </div>
                <div className="prompt-template-editor-actions">
                    <Button
                        variant="secondary"
                        size="sm"
                        onClick={handleSync}
                        isDisabled={syncDisabled}
                        data-testid="btn-sync-variables"
                    >
                        Sync Variables
                    </Button>
                </div>
            </div>
            {syncError && (
                <div className="prompt-template-editor-error">
                    <Alert
                        variant="danger"
                        isInline
                        title="Synchronization Error"
                        actionClose={<AlertActionCloseButton onClose={() => setSyncError(undefined)} />}
                    >
                        {syncError}
                    </Alert>
                </div>
            )}
            <div className="prompt-template-editor-pane">
                <RegistryCodeEditor
                    className="text-editor"
                    defaultLanguage={PROMPT_TEMPLATE_LANGUAGE_ID}
                    defaultValue={draftContentToString(props.content)}
                    onChange={handleEditorChange}
                    height="100%"
                    options={{
                        ariaLabel: PROMPT_TEMPLATE_EDITOR_ARIA_LABEL,
                        automaticLayout: true,
                        wordWrap: "on"
                    }}
                    onMount={(editor) => {
                        editorRef.current = editor;
                        // Count the variables of the text Monaco actually mounted with.
                        setCurrentText(editor.getValue());
                    }}
                />
            </div>
        </div>
    );
};
