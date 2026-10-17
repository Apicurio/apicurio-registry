import { useEffect, useRef, useState } from "react";
import type { editor } from "monaco-editor";
import type { Editor as DraftEditor, EditorProps } from "./editor-types";
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import { draftContentToString } from "@utils/content.utils.ts";

type IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

export const PromptTemplateEditor: DraftEditor = (props: EditorProps) => {
    const defaultValue = draftContentToString(props.content);
    const [value, setValue] = useState<string>(defaultValue);

    const editorRef = useRef<IStandaloneCodeEditor | undefined>(undefined);

    useEffect(() => {
        const contentString = draftContentToString(props.content);
        setValue(contentString);

        // Avoid resetting user cursor and undo history when parent re-renders with unchanged content.
        if (editorRef.current && editorRef.current.getValue() !== contentString) {
            editorRef.current.setValue(contentString);
        }
    }, [props.content]);

    return (
        <RegistryCodeEditor
            className="text-editor"
            defaultLanguage="prompt-template"
            defaultValue={value}
            onChange={props.onChange}
            height="100%"
            options={{
                automaticLayout: true,
                wordWrap: "on"
            }}
            onMount={(editor) => {
                editorRef.current = editor;
            }}
        />
    );
};
