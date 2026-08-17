import React, { useEffect, useMemo, useRef } from "react";
import { EditorProps } from "./editor-types";
import { parseJson, parseYaml, toJsonString, toYamlString } from "@utils/content.utils.ts";
import { useConfigService } from "@services/useConfigService.ts";
import { ContentTypes } from "@models/ContentTypes.ts";
import { deriveOrigin } from "@utils/url.utils.ts";

export type IframeEditorExtraData = {
    content?: never;
    features?: never;
    [key: string]: any;
};

export type IframeEditorProps = {
    editorType: "OPENAPI" | "ASYNCAPI";
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
        if (props.editorName === "OpenApiEditor") {
            console.info("[OpenApiEditor] URL location of editors: ", editorsUrl);
        }
    }, [editorsUrl, props.editorName]);

    useEffect(() => {
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-ignore
        const eventListener = (event: MessageEvent) => {
            if (!expectedOrigin || event.origin !== expectedOrigin) {
                return;
            }
            if (event.data && event.data.type === "apicurio_onChange") {
                let newContent: any = event.data.data.content;
                const currentContent = contentRef.current;
                if (typeof newContent === "object") {
                    if (currentContent.contentType === ContentTypes.APPLICATION_YAML) {
                        console.info(`[${props.editorName}] New content is 'object', converting to YAML string`);
                        newContent = toYamlString(newContent);
                    } else {
                        console.info(`[${props.editorName}] New content is 'object', converting to JSON string`);
                        newContent = toJsonString(newContent);
                    }
                } else if (typeof newContent === "string" && currentContent.contentType === ContentTypes.APPLICATION_YAML) {
                    console.info(`[${props.editorName}] Converting from JSON string to YAML string.`);
                    newContent = toYamlString(parseJson(newContent as string));
                }
                onChangeRef.current(newContent);
            }
        };
        window.addEventListener("message", eventListener, false);
        return () => {
            window.removeEventListener("message", eventListener, false);
        };
    }, [expectedOrigin, props.editorName]);

    const editorAppUrl = (): string => {
        return editorsUrl;
    };

    const onEditorLoaded = (): void => {
        // Now it's OK to post a message to iframe with the content to edit.
        let value: string;
        if (typeof props.content.content === "object") {
            console.info(`[${props.editorName}] Loading editor data from 'object' - converting to JSON string.`);
            value = toJsonString(props.content.content);
        } else if (typeof props.content.content === "string" && props.content.contentType === ContentTypes.APPLICATION_YAML) {
            console.info(`[${props.editorName}] Loading editor data from 'string' - converting from YAML to JSON.`);
            value = toJsonString(parseYaml(props.content.content as string));
        } else {
            console.info(`[${props.editorName}] Loading editor data from 'string' without content conversion.`);
            value = props.content.content as string;
        }

        const safeExtra: Record<string, any> = { ...(props.extraEditingInfo || {}) };
        delete safeExtra.content;
        delete safeExtra.features;

        const message: any = {
            type: "apicurio-editingInfo",
            data: {
                content: {
                    type: props.editorType,
                    value: value
                },
                features: {
                    allowCustomValidations: false,
                    allowImports: false
                },
                ...safeExtra
            }
        };
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
            src={editorAppUrl()}
        />
    );
};
