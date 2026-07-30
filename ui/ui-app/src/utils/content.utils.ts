import YAML from "yaml";
import { ContentTypes } from "@models/ContentTypes.ts";
import { isStringEmptyOrUndefined } from "@utils/string.utils.ts";
import { Draft, DraftContent } from "@models/drafts";
import { ArtifactTypes } from "@services/useArtifactTypesService.ts";

/**
 * Returns true if the given content is JSON formatted.
 * @param content the content to check
 */
export function isJson(content: string): boolean {
    try {
        JSON.parse(content);
        return true;
    } catch {
        return false;
    }
}
export function parseJson(content: string): any {
    return JSON.parse(content);
}
export function toJsonString(content: any): string {
    return JSON.stringify(content, null, 4);
}


/**
 * Returns true if the given content is YAML formatted.
 * @param content the content to check
 */
export function isYaml(content: string): boolean {
    try {
        const result: any = YAML.parse(content);
        if (typeof result === "object") {
            return true;
        }
    } catch {
        // Do nothing - it's not a YAML file.
    }
    return false;
}
export function parseYaml(content: string): any {
    return YAML.parse(content);
}
export function toYamlString(content: any): string {
    return YAML.stringify(content, null, 4);
}

/**
 * Returns true if the given content is XML formatted.
 * @param content the content to check
 */
export function isXml(content: string): boolean {
    try {
        const xmlParser: DOMParser = new DOMParser();
        const dom: Document = xmlParser.parseFromString(content, "application/xml");
        const isParseError: boolean = dom.getElementsByTagName("parsererror").length !== 0;
        return !isParseError;
    } catch {
        return false;
    }
}

function isXmlWithRootNode(content: string, namespace: string, localName: string): boolean {
    try {
        const xmlParser: DOMParser = new DOMParser();
        const dom: Document = xmlParser.parseFromString(content, "application/xml");
        const isParseError: boolean = dom.getElementsByTagName("parsererror").length !== 0;
        return !isParseError &&
               dom.documentElement.namespaceURI === namespace &&
               dom.documentElement.localName === localName;
    } catch {
        return false;
    }
}
export function isWsdl(content: string): boolean {
    return isXmlWithRootNode(content, "http://schemas.xmlsoap.org/wsdl/", "definitions");
}
export function isXsd(content: string): boolean {
    return isXmlWithRootNode(content, "http://www.w3.org/2001/XMLSchema", "schema");
}


/**
 * Converts a given string to something that's allowed in a filename.
 * @param value
 */
export function convertToValidFilename(value: string): string {
    return (value.replace(/[/|\\:*?"<>]/g, ""));
}

/**
 * Converts content (which might be a JS object) to a string.
 * @param content
 */
export function contentToString(content: any): string {
    if (typeof content === "string") {
        return content as string;
    } else {
        return JSON.stringify(content as string, null, 4);
    }
}


export function detectContentType(artifactType: string | undefined | null, content: string | undefined): string {
    switch (artifactType) {
        case "PROTOBUF":
            return ContentTypes.APPLICATION_PROTOBUF;
        case "WSDL":
        case "XSD":
        case "XML":
            return ContentTypes.APPLICATION_XML;
        case "GRAPHQL":
            return ContentTypes.APPLICATION_GRAPHQL;
        case "THRIFT":
            return ContentTypes.APPLICATION_THRIFT;
    }
    if (content === undefined) {
        return ContentTypes.APPLICATION_OCTET_STREAM;
    }
    if (isJson(content)) {
        return ContentTypes.APPLICATION_JSON;
    } else if (isXml(content)) {
        return ContentTypes.APPLICATION_XML;
    } else if (isYaml(content)) {
        return ContentTypes.APPLICATION_YAML;
    } else if (!isStringEmptyOrUndefined(content) && (content.indexOf("proto2") != -1 || content.indexOf("proto3") != -1)) {
        return ContentTypes.APPLICATION_PROTOBUF;
    } else {
        return ContentTypes.APPLICATION_OCTET_STREAM;
    }
}


/**
 * Detects the version of an API specification from its content.  Supports JSON or YAML
 * content that declares a top level "openapi", "asyncapi", or "swagger" property (so that
 * arbitrary content with an unrelated "info" section is not matched).  Returns the value
 * of the "info.version" property, or undefined if it is not present.
 * @param content the content to inspect
 */
export function detectVersionInContent(content: string | undefined): string | undefined {
    if (isStringEmptyOrUndefined(content)) {
        return undefined;
    }
    // Parse the content only once (this runs on every content change): try JSON first,
    // then fall back to YAML.
    let parsed: any;
    try {
        parsed = JSON.parse(content as string);
    } catch {
        try {
            parsed = YAML.parse(content as string);
        } catch {
            return undefined;
        }
    }
    if (parsed === null || typeof parsed !== "object") {
        return undefined;
    }
    const isApiSpec: boolean = parsed.openapi !== undefined || parsed.asyncapi !== undefined || parsed.swagger !== undefined;
    if (!isApiSpec) {
        return undefined;
    }
    // Both OpenAPI and AsyncAPI require "info.version" to be a string.  Anything else is
    // ignored - e.g. an unquoted YAML number like "version: 1.0" parses (lossily) to the
    // number 1, so prefilling from it would be misleading.
    const version: any = parsed.info?.version;
    if (typeof version === "string" && version.trim().length > 0) {
        return version.trim();
    }
    return undefined;
}


export function contentTypeForDraft(draft: Draft, content: DraftContent): string {
    if (content.contentType) {
        return content.contentType;
    }

    if (draft.type === ArtifactTypes.PROTOBUF) {
        return ContentTypes.APPLICATION_PROTOBUF;
    }

    return ContentTypes.APPLICATION_JSON;
}


export const draftContentToString = (content: DraftContent): string => {
    return contentToString(content.content);
};


export const draftContentToLanguage = (content: DraftContent): string => {
    if (content.contentType === ContentTypes.APPLICATION_YAML) {
        return "yaml";
    } else if (content.contentType === ContentTypes.APPLICATION_XML) {
        return "xml";
    } else if (content.contentType === ContentTypes.TEXT_XML) {
        return "xml";
    } else if (content.contentType === ContentTypes.APPLICATION_WSDL) {
        return "xml";
    } else if (content.contentType === ContentTypes.APPLICATION_GRAPHQL) {
        return "graphql";
    } else if (content.contentType === ContentTypes.APPLICATION_PROTOBUF) {
        return "protobuf";
    }
    return "json";
};

export function fileExtensionForDraft(draft: Draft, content: DraftContent): string {
    if (draft.type === ArtifactTypes.PROTOBUF) {
        return "proto";
    }

    if (content.contentType && content.contentType === ContentTypes.APPLICATION_JSON) {
        return "json";
    }
    if (content.contentType && content.contentType === ContentTypes.APPLICATION_YAML) {
        return "yaml";
    }
    if (content.contentType && content.contentType === ContentTypes.APPLICATION_XML) {
        return "xml";
    }
    if (content.contentType && content.contentType === ContentTypes.APPLICATION_WSDL) {
        return "wsdl";
    }
    if (content.contentType && content.contentType === ContentTypes.APPLICATION_GRAPHQL) {
        return "graphql";
    }

    return "txt";
}

/**
 * Called to format (pretty print) the given content.  For example, if the content is JSON
 * then the content will be parsed and then stringified with better whitespace.
 * @param value
 * @param contentType
 */
export function formatContent(value: string, contentType: string): string {
    try {
        if (contentType === ContentTypes.APPLICATION_JSON) {
            const parsed: any = JSON.parse(value);
            return JSON.stringify(parsed, null, 4);
        }
    } catch (e) {
        console.error(e);
        return value;
    }
    return value;
}
