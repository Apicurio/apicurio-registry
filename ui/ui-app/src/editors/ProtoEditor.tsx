import { MutableRefObject, useEffect, useRef, useState } from "react";
import { Editor as DraftEditor, EditorProps } from "./editor-types";
import Editor from "@monaco-editor/react";
import { editor } from "monaco-editor";
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import { draftContentToString } from "@utils/content.utils.ts";

import { registerCustomLanguages } from "./registerLanguages.ts";

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
        <Editor
            beforeMount={registerCustomLanguages}
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
