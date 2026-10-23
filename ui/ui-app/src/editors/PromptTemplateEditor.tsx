import { useEffect, useRef } from "react";
import type { editor, IDisposable } from "monaco-editor";
import type { Editor as DraftEditor, EditorProps } from "./editor-types";
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import { draftContentToString } from "@utils/content.utils.ts";
import {
    findPromptTemplateIssues,
    PROMPT_TEMPLATE_LANGUAGE_ID,
    PROMPT_TEMPLATE_MARKER_OWNER
} from "./PromptTemplateLanguage.ts";

type IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

export const PROMPT_TEMPLATE_EDITOR_ARIA_LABEL = "Prompt template source editor";

export const PromptTemplateEditor: DraftEditor = (props: EditorProps) => {
    const editorRef = useRef<IStandaloneCodeEditor | undefined>(undefined);
    const contentListenerRef = useRef<IDisposable | undefined>(undefined);

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

    useEffect(() => () => contentListenerRef.current?.dispose(), []);

    return (
        <RegistryCodeEditor
            className="text-editor"
            defaultLanguage={PROMPT_TEMPLATE_LANGUAGE_ID}
            defaultValue={draftContentToString(props.content)}
            onChange={props.onChange}
            height="100%"
            options={{
                ariaLabel: PROMPT_TEMPLATE_EDITOR_ARIA_LABEL,
                automaticLayout: true,
                wordWrap: "on"
            }}
            onMount={(editor, monaco) => {
                editorRef.current = editor;
                const model = editor.getModel();
                if (!model) {
                    return;
                }

                // Markers expose unsupported constructs to screen readers (F8), not just via color.
                const updateMarkers = (): void => {
                    const markers = findPromptTemplateIssues(monaco, model.getValue())
                        .map(issue => ({ ...issue, severity: monaco.MarkerSeverity.Warning }));
                    monaco.editor.setModelMarkers(model, PROMPT_TEMPLATE_MARKER_OWNER, markers);
                };
                updateMarkers();
                contentListenerRef.current?.dispose();
                contentListenerRef.current = model.onDidChangeContent(updateMarkers);
            }}
        />
    );
};
