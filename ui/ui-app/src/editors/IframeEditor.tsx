import React, { useEffect, useMemo, useRef } from "react";
import { EditorProps } from "./editor-types";
import { useConfigService } from "@services/useConfigService.ts";
import { deriveOrigin } from "@utils/url.utils.ts";
import {
    buildEditingInfoMessage,
    createEditorMessageListener,
    EditingInfoMessage,
    IframeEditorExtraData,
    IframeEditorType
} from "./IframeEditorProtocol";

export type IframeEditorProps = {
    editorType: IframeEditorType;
    editorName: string;
    frameId: string;
    className?: string;
    extraEditingInfo?: IframeEditorExtraData;
} & EditorProps;

/**
 * Shared IFrame-based editor bridge component.
 * Acts as a React component that bridges to external editors (OpenAPI, AsyncAPI) loaded via an iframe.
 */
export const IframeEditor: React.FunctionComponent<IframeEditorProps> = (props: IframeEditorProps) => {
    const ref = useRef<HTMLIFrameElement>(null);
    const contentRef = useRef(props.content);
    const onChangeRef = useRef(props.onChange);
    contentRef.current = props.content;
    onChangeRef.current = props.onChange;

    const config = useConfigService();

    let editorsUrl: string = config.uiEditorsUrl();
    if (editorsUrl.startsWith("/")) {
        editorsUrl = window.location.origin + editorsUrl;
    }

    const expectedOrigin = useMemo(() => {
        return deriveOrigin(editorsUrl, window.location.origin);
    }, [editorsUrl]);

    useEffect(() => {
        console.info(`[${props.editorName}] URL location of editors: `, editorsUrl);
    }, [editorsUrl, props.editorName]);

    useEffect(() => {
        const eventListener = createEditorMessageListener(expectedOrigin, props.editorName,
            () => contentRef.current.contentType, () => onChangeRef.current);
        window.addEventListener("message", eventListener, false);
        return () => {
            window.removeEventListener("message", eventListener, false);
        };
    }, [expectedOrigin, props.editorName]);

    const onEditorLoaded = (): void => {
        // Now it's OK to post a message to iframe with the content to edit.
        const message: EditingInfoMessage = buildEditingInfoMessage(props.editorType, props.editorName,
            props.content, props.extraEditingInfo);
        if (expectedOrigin && ref.current?.contentWindow) {
            ref.current.contentWindow.postMessage(message, expectedOrigin);
        }
    };

    return (
        <iframe
            id={props.frameId}
            ref={ref}
            className={props.className}
            onLoad={onEditorLoaded}
            src={editorsUrl}
        />
    );
};
