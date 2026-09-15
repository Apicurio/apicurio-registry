import React from "react";
import { Editor as DraftEditor, EditorProps } from "./editor-types";
import { IframeEditor } from "./IframeEditor";
import "./AsyncApiEditor.css";

export type AsyncApiEditorProps = {
    className?: string;
} & EditorProps;

/**
 * AsyncAPI editor. The actual editor logic is written in Angular as a separate application
 * and loaded via an iframe. This component is a bridge - it acts as a React component that
 * bridges to the iframe.
 */
export const AsyncApiEditor: DraftEditor = (props: AsyncApiEditorProps) => {
    return (
        <IframeEditor
            editorType="ASYNCAPI"
            editorName="AsyncApiEditor"
            frameId="asyncapi-editor-frame"
            className={props.className ? props.className : "editor-asyncapi-flex-container"}
            {...props}
        />
    );
};
