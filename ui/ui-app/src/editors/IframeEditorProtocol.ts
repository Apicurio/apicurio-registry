import { parseJson, parseYaml, toJsonString, toYamlString } from "@utils/content.utils.ts";
import { ContentTypes } from "@models/ContentTypes.ts";
import { DraftContent } from "@models/drafts";

export type IframeEditorType = "OPENAPI" | "ASYNCAPI";

export type IframeEditorExtraData = {
    content?: never;
    features?: never;
    [key: string]: any;
};

export type EditingInfoMessage = {
    type: "apicurio-editingInfo";
    data: {
        content: {
            type: IframeEditorType;
            value: string;
        };
        features: {
            allowCustomValidations: boolean;
            allowImports: boolean;
        };
        [key: string]: any;
    };
};

/**
 * Builds the "apicurio-editingInfo" message sent to the editor iframe once it has loaded.
 * The content is always sent to the editor as a JSON string. Keys in extraEditingInfo are
 * merged into the message data, except "content" and "features" which cannot be overridden.
 */
export const buildEditingInfoMessage = (editorType: IframeEditorType, editorName: string,
    draftContent: DraftContent, extraEditingInfo?: IframeEditorExtraData): EditingInfoMessage => {
    let value: string;
    if (typeof draftContent.content === "object") {
        console.info(`[${editorName}] Loading editor data from 'object' - converting to JSON string.`);
        value = toJsonString(draftContent.content);
    } else if (typeof draftContent.content === "string" && draftContent.contentType === ContentTypes.APPLICATION_YAML) {
        console.info(`[${editorName}] Loading editor data from 'string' - converting from YAML to JSON.`);
        value = toJsonString(parseYaml(draftContent.content as string));
    } else {
        console.info(`[${editorName}] Loading editor data from 'string' without content conversion.`);
        value = draftContent.content as string;
    }

    const safeExtra: Record<string, any> = { ...(extraEditingInfo || {}) };
    delete safeExtra.content;
    delete safeExtra.features;

    return {
        type: "apicurio-editingInfo",
        data: {
            ...safeExtra,
            content: {
                type: editorType,
                value: value
            },
            features: {
                allowCustomValidations: false,
                allowImports: false
            }
        }
    };
};

/**
 * Creates the window "message" listener that receives content changes from the editor iframe.
 * Messages from any origin other than expectedOrigin are ignored (fails closed when no origin is
 * known). Received content is converted to match the draft's content type (JSON or YAML) before
 * being passed to onChange. Content type and onChange are read through getters so that the
 * listener always sees the latest values without needing to be re-registered.
 */
export const createEditorMessageListener = (expectedOrigin: string | undefined, editorName: string,
    getContentType: () => string, getOnChange: () => (value: any) => void): (event: MessageEvent) => void => {
    return (event: MessageEvent): void => {
        if (!expectedOrigin || event.origin !== expectedOrigin) {
            return;
        }
        if (event.data && event.data.type === "apicurio_onChange") {
            let newContent: any = event.data.data?.content;
            const contentType: string = getContentType();
            if (typeof newContent === "object") {
                if (contentType === ContentTypes.APPLICATION_YAML) {
                    console.info(`[${editorName}] New content is 'object', converting to YAML string`);
                    newContent = toYamlString(newContent);
                } else {
                    console.info(`[${editorName}] New content is 'object', converting to JSON string`);
                    newContent = toJsonString(newContent);
                }
            } else if (typeof newContent === "string" && contentType === ContentTypes.APPLICATION_YAML) {
                console.info(`[${editorName}] Converting from JSON string to YAML string.`);
                newContent = toYamlString(parseJson(newContent as string));
            }
            getOnChange()(newContent);
        }
    };
};
