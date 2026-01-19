import { info } from "@quickjs4j/ArtifactTypeScriptProvider_Builtins.mjs"
import { parse, stringify } from "yaml"

type TypedContent = {
    contentType: string;
    content: string;
}

export function acceptsContent(request: any): boolean {
    info(">>> acceptsContent contentType: " + request.typedContent.contentType);

    let accepted: boolean = false;
    if (request.typedContent.contentType === "application/x-yaml") {
        const content: string = request.typedContent.content;
        if (content.startsWith("#%RAML 1.0")) {
            accepted = true;
        }
    }
    return accepted;
}

export function testCompatibility(request: any): any {
    const existingArtifacts: TypedContent[] = request.existingArtifacts;
    const proposedArtifact: TypedContent = request.proposedArtifact;

    if (existingArtifacts == null || existingArtifacts == undefined || existingArtifacts.length === 0) {
        return {
            incompatibleDifferences: []
        }
    }

    try {
        const proposedRoot: any = parse(proposedArtifact.content);
        const existingRoot: any = parse(existingArtifacts[0].content);

        const proposedVersion: string = proposedRoot.version;
        const existingVersion: string = existingRoot.version;

        if (proposedVersion === existingVersion) {
            return {
                incompatibleDifferences: [
                    {
                        description: "Expected new version number but found identical versions: " + proposedVersion,
                        context: "/"
                    }
                ]
            }
        }
    } catch (e) {
        return {
            incompatibleDifferences: [
                {
                    description: "Error during compatibility check: " + e,
                    context: "/"
                }
            ]
        }
    }
    return {
        incompatibleDifferences: []
    }
}

export function canonicalize(request: any): any {
    info(">>> canonicalize contentType: " + request.content.contentType);

    const originalContent: string = request.content.content;
    const convertedContent: string = originalContent
        .replace("use to query all orders of a user", "USE TO QUERY ALL ORDERS OF A USER")
        .replace("Lists all orders of a specific user", "LIST ALL ORDERS OF A SPECIFIC USER");

    return {
        typedContent: {
            contentType: request.content.contentType,
            content: convertedContent
        }
    };
}

export function dereference(request: any): any {
    const root: any = parse(request.content.content);
    const indexedResolvedRefs: any = {};
    request.resolvedReferences.forEach((resolvedRef: any) => {
        indexedResolvedRefs[resolvedRef.name] = {
            contentType: resolvedRef.contentType,
            content: resolvedRef.content
        }
    });
    deref(root, indexedResolvedRefs);
    const dereferencedContent: string = "---\n" + stringify(root, null, {
        defaultKeyType: "PLAIN",
        defaultStringType: "QUOTE_DOUBLE",
        indentSeq: false,
        lineWidth: 0
    });
    return {
        typedContent: {
            contentType: request.content.contentType,
            content: dereferencedContent
        }
    }
}


function deref(node: any, indexedResolvedRefs: any): void {
    if (node !== null && node !== undefined) {
        if (typeof node === "object") {
            const objectNode: any = node;
            Object.keys(objectNode).forEach(fieldName => {
                const fieldNode: any = objectNode[fieldName];
                if (typeof fieldNode === "string") {
                    const textValue: string = fieldNode as string;
                    if (textValue.startsWith("~include")) {
                        const includeName: string = textValue.substring("~include ".length);
                        if (indexedResolvedRefs[includeName]) {
                            const includeContent: TypedContent = indexedResolvedRefs[includeName];
                            objectNode[fieldName] = includeContent.content;
                        }
                    }
                } else {
                    deref(fieldNode, indexedResolvedRefs);
                }
            });
        } else if (Array.isArray(node)) {
            const array: any[] = node;
            array.forEach(childNode => {
                if (childNode != null && childNode !== undefined) {
                    deref(childNode, indexedResolvedRefs);
                }
            })
        }
    }
}


export function rewriteReferences(request: any): any {
    const root: any = parse(request.content.content);
    const indexedResolvedReferenceUrls: any = {};
    request.resolvedReferenceUrls.forEach((resolvedRefUrl: any) => {
        indexedResolvedReferenceUrls[resolvedRefUrl.name] = resolvedRefUrl.url;
    });
    rewrite(root, indexedResolvedReferenceUrls);
    const dereferencedContent: string = "---\n" + stringify(root, null, {
        defaultKeyType: "PLAIN",
        defaultStringType: "QUOTE_DOUBLE",
        indentSeq: false,
        lineWidth: 0
    });
    return {
        typedContent: {
            contentType: request.content.contentType,
            content: dereferencedContent
        }
    }
}

function rewrite(node: any, indexedResolvedReferenceUrls: any): void {
    if (node !== null && node !== undefined) {
        if (typeof node === "object") {
            const objectNode: any = node;
            Object.keys(objectNode).forEach(fieldName => {
                const fieldNode: any = objectNode[fieldName];
                if (typeof fieldNode === "string") {
                    const textValue: string = fieldNode as string;
                    if (textValue.startsWith("~include")) {
                        const includeName: string = textValue.substring("~include ".length);
                        if (indexedResolvedReferenceUrls[includeName]) {
                            const refUrl: string = indexedResolvedReferenceUrls[includeName];
                            objectNode[fieldName] = "~include " + refUrl;
                        }
                    }
                } else {
                    rewrite(fieldNode, indexedResolvedReferenceUrls);
                }
            });
        } else if (Array.isArray(node)) {
            const array: any[] = node;
            array.forEach(childNode => {
                if (childNode != null && childNode !== undefined) {
                    rewrite(childNode, indexedResolvedReferenceUrls);
                }
            })
        }
    }
}


export function validate(request: any): any {
    const violations: any[] = [];
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    if (contentType !== "application/x-yaml") {
        violations.push(
            {
                description: "Incorrect content type.  Expected 'application/x-yaml' but found '" + contentType + "'.",
                context: null
            }
        );
    } else {
        if (!content.startsWith("#%RAML 1.0")) {
            violations.push(
                {
                    description: "Missing '#%RAML 1.0' content header.",
                    context: null
                }
            );
        } else {
            // TODO: parse the YAML
        }
    }
    return {
        ruleViolations: violations
    };
}

export function validateReferences(request: any): any {
    // TODO implement this once there is a test for it
    request;
    return {};
}

export function findExternalReferences(request: any): any {
    request;
    return {};
}
