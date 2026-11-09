import { useEffect, useRef } from "react";
import type { editor } from "monaco-editor";
import type { Editor as DraftEditor, EditorProps } from "./editor-types";
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import { draftContentToString } from "@utils/content.utils.ts";
import { PROMPT_TEMPLATE_LANGUAGE_ID } from "./PromptTemplateLanguage.ts";

type IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

export const PROMPT_TEMPLATE_EDITOR_ARIA_LABEL = "Prompt template source editor";

export const PromptTemplateEditor: DraftEditor = (props: EditorProps) => {
    const editorRef = useRef<IStandaloneCodeEditor | undefined>(undefined);

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
            onMount={(editor) => {
                editorRef.current = editor;
            }}
        />
    );
};
