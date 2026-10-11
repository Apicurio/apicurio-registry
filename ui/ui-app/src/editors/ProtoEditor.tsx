import { MutableRefObject, useEffect, useRef, useState } from "react";
import { Editor as DraftEditor, EditorProps } from "./editor-types";
import { RegistryCodeEditor } from "@app/components/codeEditor/RegistryEditors.tsx";
import type { editor } from "monaco-editor";
type IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import { draftContentToString } from "@utils/content.utils.ts";

export const ProtoEditor: DraftEditor = (props: EditorProps) => {
    const defaultValue: string = draftContentToString(props.content);
    const [value, setValue] = useState<string>(defaultValue);

    const editorRef: MutableRefObject<IStandaloneCodeEditor|undefined> = useRef<IStandaloneCodeEditor>(undefined);

    useEffect(() => {
        const contentString: string = draftContentToString(props.content);
        setValue(contentString);

        if (editorRef.current) {
            editorRef.current?.setValue(contentString);
        }
    }, [props.content]);



    return (
        <RegistryCodeEditor
            className="text-editor"
            defaultLanguage="protobuf"
            defaultValue={value}
            onChange={props.onChange}
            height="100%"
            options={{
                automaticLayout: true
            }}
            onMount={(editor) => {
                editorRef.current = editor;
            }}
        />
    );
};
