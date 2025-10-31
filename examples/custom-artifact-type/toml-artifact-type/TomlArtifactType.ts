import type {
    ContentAccepterRequest,
    CompatibilityCheckerRequest,
    CompatibilityCheckerResponse,
    ContentCanonicalizerRequest,
    ContentCanonicalizerResponse,
    ContentDereferencerRequest,
    ContentDereferencerResponse,
    ContentValidatorRequest,
    ContentValidatorResponse,
    ReferenceFinderRequest,
    ReferenceFinderResponse
} from '@apicurio/artifact-type-builtins';

type TypedContent = {
    contentType: string;
    content: string;
}

/**
 * Simple TOML parser for basic validation
 * This is a minimal implementation just to demonstrate the concept
 */
function isValidToml(content: string): boolean {
    const lines = content.split('\n');
    for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed === '' || trimmed.startsWith('#')) {
            continue;
        }
        // Check for section headers [section]
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
            continue;
        }
        // Check for key-value pairs
        if (trimmed.includes('=')) {
            continue;
        }
        // If line is not empty, comment, section, or key-value, it's invalid
        if (trimmed.length > 0) {
            return false;
        }
    }
    return true;
}

/**
 * Check if the content is TOML format
 */
export function acceptsContent(request: ContentAccepterRequest): boolean {
    let accepted: boolean = false;
    if (request.typedContent.contentType === "application/toml") {
        const content: string = request.typedContent.content;
        accepted = isValidToml(content);
    }
    return accepted;
}

/**
 * Test compatibility between existing and proposed artifacts
 * For this simple example, we just ensure section names haven't been removed
 */
export function testCompatibility(request: CompatibilityCheckerRequest): CompatibilityCheckerResponse {
    const existingArtifacts: TypedContent[] = request.existingArtifacts;
    const proposedArtifact: TypedContent = request.proposedArtifact;

    if (existingArtifacts == null || existingArtifacts == undefined || existingArtifacts.length === 0) {
        return {
            incompatibleDifferences: []
        }
    }

    try {
        const proposedSections = extractSections(proposedArtifact.content);
        const existingSections = extractSections(existingArtifacts[0].content);

        const incompatibleDifferences: any[] = [];

        // Check if any sections were removed
        for (const section of existingSections) {
            if (!proposedSections.includes(section)) {
                incompatibleDifferences.push({
                    description: "Section '" + section + "' was removed",
                    context: "/" + section
                });
            }
        }

        return {
            incompatibleDifferences: incompatibleDifferences
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
}

/**
 * Extract section names from TOML content
 */
function extractSections(content: string): string[] {
    const sections: string[] = [];
    const lines = content.split('\n');
    for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
            sections.push(trimmed.substring(1, trimmed.length - 1));
        }
    }
    return sections;
}

/**
 * Canonicalize TOML content by normalizing whitespace and sorting sections
 */
export function canonicalize(request: ContentCanonicalizerRequest): ContentCanonicalizerResponse {
    const originalContent: string = request.content.content;

    // Simple canonicalization: uppercase all keys
    const convertedContent: string = originalContent
        .split('\n')
        .map(line => {
            const trimmed = line.trim();
            if (trimmed.includes('=') && !trimmed.startsWith('#')) {
                const parts = line.split('=');
                return parts[0].toUpperCase() + '=' + parts.slice(1).join('=');
            }
            return line;
        })
        .join('\n');

    return {
        typedContent: {
            contentType: request.content.contentType,
            content: convertedContent
        }
    };
}

/**
 * Validate TOML content structure
 */
export function validate(request: ContentValidatorRequest): ContentValidatorResponse {
    const violations: any[] = [];
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    if (contentType !== "application/toml") {
        violations.push(
            {
                description: "Incorrect content type.  Expected 'application/toml' but found '" + contentType + "'.",
                context: null
            }
        );
    } else {
        if (!isValidToml(content)) {
            violations.push(
                {
                    description: "Invalid TOML format detected.",
                    context: null
                }
            );
        }
    }

    return {
        ruleViolations: violations
    };
}

/**
 * Dereference content by replacing references with actual content
 */
export function dereference(request: ContentDereferencerRequest): ContentDereferencerResponse {
    const content: string = request.content.content;
    const indexedResolvedRefs: any = {};

    request.resolvedReferences.forEach((resolvedRef: any) => {
        indexedResolvedRefs[resolvedRef.name] = {
            contentType: resolvedRef.contentType,
            content: resolvedRef.content
        }
    });

    let dereferencedContent = content;

    // Replace references in format: @include "filename"
    const lines = content.split('\n');
    const resultLines = lines.map(line => {
        const includeMatch = line.match(/@include\s+"([^"]+)"/);
        if (includeMatch && indexedResolvedRefs[includeMatch[1]]) {
            const includeContent: TypedContent = indexedResolvedRefs[includeMatch[1]];
            return "# Included from " + includeMatch[1] + "\n" + includeContent.content;
        }
        return line;
    });

    dereferencedContent = resultLines.join('\n');

    return {
        typedContent: {
            contentType: request.content.contentType,
            content: dereferencedContent
        }
    }
}

/**
 * Rewrite references to use URLs instead of file paths
 */
export function rewriteReferences(request: ContentDereferencerRequest): ContentDereferencerResponse {
    const content: string = request.content.content;
    const indexedResolvedReferenceUrls: any = {};

    request.resolvedReferenceUrls.forEach((resolvedRefUrl: any) => {
        indexedResolvedReferenceUrls[resolvedRefUrl.name] = resolvedRefUrl.url;
    });

    let rewrittenContent = content;

    // Replace references with URLs
    const lines = content.split('\n');
    const resultLines = lines.map(line => {
        const includeMatch = line.match(/@include\s+"([^"]+)"/);
        if (includeMatch && indexedResolvedReferenceUrls[includeMatch[1]]) {
            const refUrl: string = indexedResolvedReferenceUrls[includeMatch[1]];
            return '@include "' + refUrl + '"';
        }
        return line;
    });

    rewrittenContent = resultLines.join('\n');

    return {
        typedContent: {
            contentType: request.content.contentType,
            content: rewrittenContent
        }
    }
}

/**
 * Find external references in the content
 */
export function findExternalReferences(request: ReferenceFinderRequest): ReferenceFinderResponse {
    const content: string = request.typedContent.content;
    const references: string[] = [];

    const lines = content.split('\n');
    for (const line of lines) {
        const includeMatch = line.match(/@include\s+"([^"]+)"/);
        if (includeMatch) {
            references.push(includeMatch[1]);
        }
    }

    return {
        externalReferences: references
    };
}

/**
 * Validate that all references are properly mapped
 */
export function validateReferences(request: ContentValidatorRequest): ContentValidatorResponse {
    const violations: any[] = [];
    const mappedReferences: string[] = request.resolvedReferences || [];
    const foundReferences = findExternalReferences({
        typedContent: request.content
    });

    for (const ref of foundReferences.externalReferences) {
        if (!mappedReferences.includes(ref)) {
            violations.push({
                description: "Reference '" + ref + "' is not mapped",
                context: null
            });
        }
    }

    return {
        ruleViolations: violations
    };
}