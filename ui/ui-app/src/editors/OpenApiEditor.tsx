import React, { useEffect, useState } from "react";
import { Editor as DraftEditor, EditorProps } from "./editor-types";
import { IfNotLoading } from "@apitomy/common-ui-components";
import { IframeEditor } from "./IframeEditor";
import "./OpenApiEditor.css";

export type OpenApiEditorProps = {
    className?: string;
} & EditorProps;

/**
 * OpenAPI editor. The actual editor logic is written in Angular as a separate application
 * and loaded via an iframe. This component is a bridge - it acts as a React component that
 * bridges to the iframe.
 */
export const OpenApiEditor: DraftEditor = (props: OpenApiEditorProps) => {
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        setIsLoading(false);
    }, []);

    return (
        <IfNotLoading isLoading={isLoading}>
            <IframeEditor
                editorType="OPENAPI"
                editorName="OpenApiEditor"
                frameId="openapi-editor-frame"
                className={props.className ? props.className : "editor-openapi-flex-container"}
                extraEditingInfo={{
                    openapi: {
                        vendorExtensions: []
                    }
                }}
                {...props}
            />
        </IfNotLoading>
    );
};
