import { info } from "@quickjs4j/ArtifactTypeScriptProvider_Builtins.mjs";
import { parse, stringify } from "yaml";
export function acceptsContent(request) {
    info(">>> acceptsContent contentType: " + request.typedContent.contentType);
    let accepted = false;
    if (request.typedContent.contentType === "application/x-yaml") {
        const content = request.typedContent.content;
        if (content.startsWith("#%RAML 1.0")) {
            accepted = true;
        }
    }
    return accepted;
}
export function testCompatibility(request) {
    const existingArtifacts = request.existingArtifacts;
    const proposedArtifact = request.proposedArtifact;
    if (existingArtifacts == null || existingArtifacts == undefined || existingArtifacts.length === 0) {
        return {
            incompatibleDifferences: []
        };
    }
    try {
        const proposedRoot = parse(proposedArtifact.content);
        const existingRoot = parse(existingArtifacts[0].content);
        const proposedVersion = proposedRoot.version;
        const existingVersion = existingRoot.version;
        if (proposedVersion === existingVersion) {
            return {
                incompatibleDifferences: [
                    {
                        description: "Expected new version number but found identical versions: " + proposedVersion,
                        context: "/"
                    }
                ]
            };
        }
    }
    catch (e) {
        return {
            incompatibleDifferences: [
                {
                    description: "Error during compatibility check: " + e,
                    context: "/"
                }
            ]
        };
    }
    return {
        incompatibleDifferences: []
    };
}
export function canonicalize(request) {
    info(">>> canonicalize contentType: " + request.content.contentType);
    const originalContent = request.content.content;
    const convertedContent = originalContent
        .replace("use to query all orders of a user", "USE TO QUERY ALL ORDERS OF A USER")
        .replace("Lists all orders of a specific user", "LIST ALL ORDERS OF A SPECIFIC USER");
    return {
        typedContent: {
            contentType: request.content.contentType,
            content: convertedContent
        }
    };
}
export function dereference(request) {
    const root = parse(request.content.content);
    const indexedResolvedRefs = {};
    request.resolvedReferences.forEach((resolvedRef) => {
        indexedResolvedRefs[resolvedRef.name] = {
            contentType: resolvedRef.contentType,
            content: resolvedRef.content
        };
    });
    deref(root, indexedResolvedRefs);
    const dereferencedContent = "---\n" + stringify(root, null, {
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
    };
}
function deref(node, indexedResolvedRefs) {
    if (node !== null && node !== undefined) {
        if (typeof node === "object") {
            const objectNode = node;
            Object.keys(objectNode).forEach(fieldName => {
                const fieldNode = objectNode[fieldName];
                if (typeof fieldNode === "string") {
                    const textValue = fieldNode;
                    if (textValue.startsWith("~include")) {
                        const includeName = textValue.substring("~include ".length);
                        if (indexedResolvedRefs[includeName]) {
                            const includeContent = indexedResolvedRefs[includeName];
                            objectNode[fieldName] = includeContent.content;
                        }
                    }
                }
                else {
                    deref(fieldNode, indexedResolvedRefs);
                }
            });
        }
        else if (Array.isArray(node)) {
            const array = node;
            array.forEach(childNode => {
                if (childNode != null && childNode !== undefined) {
                    deref(childNode, indexedResolvedRefs);
                }
            });
        }
    }
}
export function rewriteReferences(request) {
    const root = parse(request.content.content);
    const indexedResolvedReferenceUrls = {};
    request.resolvedReferenceUrls.forEach((resolvedRefUrl) => {
        indexedResolvedReferenceUrls[resolvedRefUrl.name] = resolvedRefUrl.url;
    });
    rewrite(root, indexedResolvedReferenceUrls);
    const dereferencedContent = "---\n" + stringify(root, null, {
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
    };
}
function rewrite(node, indexedResolvedReferenceUrls) {
    if (node !== null && node !== undefined) {
        if (typeof node === "object") {
            const objectNode = node;
            Object.keys(objectNode).forEach(fieldName => {
                const fieldNode = objectNode[fieldName];
                if (typeof fieldNode === "string") {
                    const textValue = fieldNode;
                    if (textValue.startsWith("~include")) {
                        const includeName = textValue.substring("~include ".length);
                        if (indexedResolvedReferenceUrls[includeName]) {
                            const refUrl = indexedResolvedReferenceUrls[includeName];
                            objectNode[fieldName] = "~include " + refUrl;
                        }
                    }
                }
                else {
                    rewrite(fieldNode, indexedResolvedReferenceUrls);
                }
            });
        }
        else if (Array.isArray(node)) {
            const array = node;
            array.forEach(childNode => {
                if (childNode != null && childNode !== undefined) {
                    rewrite(childNode, indexedResolvedReferenceUrls);
                }
            });
        }
    }
}
export function validate(request) {
    const violations = [];
    const content = request.content.content;
    const contentType = request.content.contentType;
    if (contentType !== "application/x-yaml") {
        violations.push({
            description: "Incorrect content type.  Expected 'application/x-yaml' but found '" + contentType + "'.",
            context: null
        });
    }
    else {
        if (!content.startsWith("#%RAML 1.0")) {
            violations.push({
                description: "Missing '#%RAML 1.0' content header.",
                context: null
            });
        }
        else {
            // TODO: parse the YAML
        }
    }
    return {
        ruleViolations: violations
    };
}
export function validateReferences(request) {
    // TODO implement this once there is a test for it
    request;
    return {};
}
export function findExternalReferences(request) {
    request;
    return {};
}
