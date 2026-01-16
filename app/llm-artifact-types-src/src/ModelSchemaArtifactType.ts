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
import { parse as parseYaml, stringify as stringifyYaml } from 'yaml';

type TypedContent = {
    contentType: string;
    content: string;
}

interface ModelSchema {
    $schema?: string;
    modelId: string;
    provider?: string;
    version?: string;
    input?: Record<string, unknown>;
    output?: Record<string, unknown>;
    metadata?: Record<string, unknown>;
    definitions?: Record<string, unknown>;
}

/**
 * Parse content as JSON or YAML based on content type
 */
function parseContent(content: string, contentType: string): ModelSchema | null {
    try {
        if (contentType === 'application/json') {
            return JSON.parse(content);
        } else if (contentType === 'application/x-yaml' || contentType === 'application/yaml') {
            return parseYaml(content) as ModelSchema;
        }
        // Try JSON first, then YAML
        try {
            return JSON.parse(content);
        } catch {
            return parseYaml(content) as ModelSchema;
        }
    } catch {
        return null;
    }
}

/**
 * Check if the content is a MODEL_SCHEMA artifact type
 */
export function acceptsContent(request: ContentAccepterRequest): boolean {
    const content: string = request.typedContent.content;
    const contentType: string = request.typedContent.contentType;

    // Only accept JSON or YAML content types
    if (contentType !== 'application/json' &&
        contentType !== 'application/x-yaml' &&
        contentType !== 'application/yaml') {
        return false;
    }

    const parsed = parseContent(content, contentType);
    if (!parsed) {
        return false;
    }

    // Check for $schema containing 'model-schema'
    if (parsed.$schema && parsed.$schema.includes('model-schema')) {
        return true;
    }

    // Check for presence of modelId + (input or output)
    if (parsed.modelId && (parsed.input || parsed.output)) {
        return true;
    }

    return false;
}

/**
 * Validate MODEL_SCHEMA content structure
 */
export function validate(request: ContentValidatorRequest): ContentValidatorResponse {
    const violations: Array<{ description: string; context: string | null }> = [];
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    // Validate content type
    if (contentType !== 'application/json' &&
        contentType !== 'application/x-yaml' &&
        contentType !== 'application/yaml') {
        violations.push({
            description: `Incorrect content type. Expected 'application/json' or 'application/x-yaml' but found '${contentType}'.`,
            context: null
        });
        return { ruleViolations: violations };
    }

    // Parse content
    const parsed = parseContent(content, contentType);
    if (!parsed) {
        violations.push({
            description: 'Invalid content format. Unable to parse as JSON or YAML.',
            context: null
        });
        return { ruleViolations: violations };
    }

    // Validate required field: modelId
    if (!parsed.modelId || typeof parsed.modelId !== 'string') {
        violations.push({
            description: "Missing or invalid required field 'modelId'. Must be a non-empty string.",
            context: '/modelId'
        });
    }

    // Validate that at least one of input or output is present
    if (!parsed.input && !parsed.output) {
        violations.push({
            description: "At least one of 'input' or 'output' schema must be defined.",
            context: '/'
        });
    }

    // Validate input schema structure if present
    if (parsed.input) {
        if (typeof parsed.input !== 'object') {
            violations.push({
                description: "Field 'input' must be an object (JSON Schema).",
                context: '/input'
            });
        }
    }

    // Validate output schema structure if present
    if (parsed.output) {
        if (typeof parsed.output !== 'object') {
            violations.push({
                description: "Field 'output' must be an object (JSON Schema).",
                context: '/output'
            });
        }
    }

    // Validate metadata structure if present
    if (parsed.metadata !== undefined && typeof parsed.metadata !== 'object') {
        violations.push({
            description: "Field 'metadata' must be an object if provided.",
            context: '/metadata'
        });
    }

    return { ruleViolations: violations };
}

/**
 * Extract required fields from a JSON Schema properties object
 */
function getRequiredFields(schema: Record<string, unknown> | undefined): string[] {
    if (!schema || typeof schema !== 'object') {
        return [];
    }
    const required = (schema as { required?: string[] }).required;
    return Array.isArray(required) ? required : [];
}

/**
 * Get property names from a JSON Schema
 */
function getPropertyNames(schema: Record<string, unknown> | undefined): string[] {
    if (!schema || typeof schema !== 'object') {
        return [];
    }
    const properties = (schema as { properties?: Record<string, unknown> }).properties;
    if (!properties || typeof properties !== 'object') {
        return [];
    }
    return Object.keys(properties);
}

/**
 * Get property type from a JSON Schema property
 */
function getPropertyType(schema: Record<string, unknown> | undefined, propName: string): string | null {
    if (!schema || typeof schema !== 'object') {
        return null;
    }
    const properties = (schema as { properties?: Record<string, unknown> }).properties;
    if (!properties || typeof properties !== 'object') {
        return null;
    }
    const prop = properties[propName] as { type?: string } | undefined;
    return prop?.type || null;
}

/**
 * Test compatibility between existing and proposed MODEL_SCHEMA artifacts
 * BACKWARD compatibility rules:
 * - Cannot remove required input fields
 * - Cannot add new required input fields
 * - Cannot change field types
 * - Output schema can expand but not contract
 */
export function testCompatibility(request: CompatibilityCheckerRequest): CompatibilityCheckerResponse {
    const existingArtifacts: TypedContent[] = request.existingArtifacts;
    const proposedArtifact: TypedContent = request.proposedArtifact;

    if (!existingArtifacts || existingArtifacts.length === 0) {
        return { incompatibleDifferences: [] };
    }

    const incompatibleDifferences: Array<{ description: string; context: string }> = [];

    try {
        const proposed = parseContent(proposedArtifact.content, proposedArtifact.contentType);
        const existing = parseContent(existingArtifacts[0].content, existingArtifacts[0].contentType);

        if (!proposed || !existing) {
            return {
                incompatibleDifferences: [{
                    description: 'Unable to parse content for compatibility check',
                    context: '/'
                }]
            };
        }

        // Check input schema compatibility
        if (existing.input && proposed.input) {
            const existingInputRequired = getRequiredFields(existing.input);
            const proposedInputRequired = getRequiredFields(proposed.input);

            // Cannot add new required input fields (breaks existing consumers)
            for (const field of proposedInputRequired) {
                if (!existingInputRequired.includes(field)) {
                    incompatibleDifferences.push({
                        description: `New required input field '${field}' was added. This breaks backward compatibility.`,
                        context: `/input/required/${field}`
                    });
                }
            }

            // Check for removed input properties
            const existingInputProps = getPropertyNames(existing.input);
            const proposedInputProps = getPropertyNames(proposed.input);

            for (const prop of existingInputProps) {
                if (!proposedInputProps.includes(prop)) {
                    incompatibleDifferences.push({
                        description: `Input property '${prop}' was removed.`,
                        context: `/input/properties/${prop}`
                    });
                }
            }

            // Check for type changes in input properties
            for (const prop of existingInputProps) {
                if (proposedInputProps.includes(prop)) {
                    const existingType = getPropertyType(existing.input, prop);
                    const proposedType = getPropertyType(proposed.input, prop);
                    if (existingType && proposedType && existingType !== proposedType) {
                        incompatibleDifferences.push({
                            description: `Input property '${prop}' type changed from '${existingType}' to '${proposedType}'.`,
                            context: `/input/properties/${prop}/type`
                        });
                    }
                }
            }
        } else if (existing.input && !proposed.input) {
            incompatibleDifferences.push({
                description: "Input schema was removed.",
                context: '/input'
            });
        }

        // Check output schema compatibility - can expand but not contract
        if (existing.output && proposed.output) {
            const existingOutputProps = getPropertyNames(existing.output);
            const proposedOutputProps = getPropertyNames(proposed.output);

            for (const prop of existingOutputProps) {
                if (!proposedOutputProps.includes(prop)) {
                    incompatibleDifferences.push({
                        description: `Output property '${prop}' was removed.`,
                        context: `/output/properties/${prop}`
                    });
                }
            }

            // Check for type changes in output properties
            for (const prop of existingOutputProps) {
                if (proposedOutputProps.includes(prop)) {
                    const existingType = getPropertyType(existing.output, prop);
                    const proposedType = getPropertyType(proposed.output, prop);
                    if (existingType && proposedType && existingType !== proposedType) {
                        incompatibleDifferences.push({
                            description: `Output property '${prop}' type changed from '${existingType}' to '${proposedType}'.`,
                            context: `/output/properties/${prop}/type`
                        });
                    }
                }
            }
        } else if (existing.output && !proposed.output) {
            incompatibleDifferences.push({
                description: "Output schema was removed.",
                context: '/output'
            });
        }

    } catch (e) {
        return {
            incompatibleDifferences: [{
                description: `Error during compatibility check: ${e}`,
                context: '/'
            }]
        };
    }

    return { incompatibleDifferences };
}

/**
 * Sort object keys recursively for canonicalization
 */
function sortObjectKeys(obj: unknown): unknown {
    if (obj === null || typeof obj !== 'object') {
        return obj;
    }
    if (Array.isArray(obj)) {
        return obj.map(sortObjectKeys);
    }
    const sorted: Record<string, unknown> = {};
    const keys = Object.keys(obj as Record<string, unknown>).sort();
    for (const key of keys) {
        sorted[key] = sortObjectKeys((obj as Record<string, unknown>)[key]);
    }
    return sorted;
}

/**
 * Canonicalize MODEL_SCHEMA content by normalizing JSON structure
 */
export function canonicalize(request: ContentCanonicalizerRequest): ContentCanonicalizerResponse {
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    try {
        const parsed = parseContent(content, contentType);
        if (!parsed) {
            return {
                typedContent: {
                    contentType: contentType,
                    content: content
                }
            };
        }

        // Sort keys recursively
        const sorted = sortObjectKeys(parsed);

        // Always output as JSON for canonical form
        const canonicalContent = JSON.stringify(sorted, null, 2);

        return {
            typedContent: {
                contentType: 'application/json',
                content: canonicalContent
            }
        };
    } catch {
        return {
            typedContent: {
                contentType: contentType,
                content: content
            }
        };
    }
}

/**
 * Find all $ref references in an object recursively
 */
function findRefs(obj: unknown, refs: string[]): void {
    if (obj === null || typeof obj !== 'object') {
        return;
    }
    if (Array.isArray(obj)) {
        for (const item of obj) {
            findRefs(item, refs);
        }
        return;
    }
    const record = obj as Record<string, unknown>;
    if (typeof record.$ref === 'string') {
        const ref = record.$ref;
        // Only add external references (not internal #/definitions/...)
        if (!ref.startsWith('#') && !refs.includes(ref)) {
            refs.push(ref);
        }
    }
    for (const key of Object.keys(record)) {
        findRefs(record[key], refs);
    }
}

/**
 * Find external references ($ref) in the MODEL_SCHEMA content
 */
export function findExternalReferences(request: ReferenceFinderRequest): ReferenceFinderResponse {
    const content: string = request.typedContent.content;
    const contentType: string = request.typedContent.contentType;

    const references: string[] = [];

    try {
        const parsed = parseContent(content, contentType);
        if (parsed) {
            findRefs(parsed, references);
        }
    } catch {
        // Return empty references on parse error
    }

    return { externalReferences: references };
}

/**
 * Resolve $ref references in an object recursively
 */
function resolveRefs(obj: unknown, indexedRefs: Record<string, TypedContent>): unknown {
    if (obj === null || typeof obj !== 'object') {
        return obj;
    }
    if (Array.isArray(obj)) {
        return obj.map(item => resolveRefs(item, indexedRefs));
    }
    const record = obj as Record<string, unknown>;
    if (typeof record.$ref === 'string') {
        const ref = record.$ref;
        // Resolve external references
        if (!ref.startsWith('#') && indexedRefs[ref]) {
            const resolved = parseContent(indexedRefs[ref].content, indexedRefs[ref].contentType);
            if (resolved) {
                return resolved;
            }
        }
    }
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(record)) {
        result[key] = resolveRefs(record[key], indexedRefs);
    }
    return result;
}

/**
 * Dereference content by replacing $ref references with actual content
 */
export function dereference(request: ContentDereferencerRequest): ContentDereferencerResponse {
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    const indexedResolvedRefs: Record<string, TypedContent> = {};
    request.resolvedReferences.forEach((resolvedRef: { name: string; contentType: string; content: string }) => {
        indexedResolvedRefs[resolvedRef.name] = {
            contentType: resolvedRef.contentType,
            content: resolvedRef.content
        };
    });

    try {
        const parsed = parseContent(content, contentType);
        if (!parsed) {
            return {
                typedContent: {
                    contentType: contentType,
                    content: content
                }
            };
        }

        const dereferenced = resolveRefs(parsed, indexedResolvedRefs);
        const dereferencedContent = JSON.stringify(dereferenced, null, 2);

        return {
            typedContent: {
                contentType: 'application/json',
                content: dereferencedContent
            }
        };
    } catch {
        return {
            typedContent: {
                contentType: contentType,
                content: content
            }
        };
    }
}

/**
 * Rewrite references to use URLs instead of file paths
 */
export function rewriteReferences(request: ContentDereferencerRequest): ContentDereferencerResponse {
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    const indexedResolvedUrls: Record<string, string> = {};
    request.resolvedReferenceUrls.forEach((resolvedRefUrl: { name: string; url: string }) => {
        indexedResolvedUrls[resolvedRefUrl.name] = resolvedRefUrl.url;
    });

    try {
        const parsed = parseContent(content, contentType);
        if (!parsed) {
            return {
                typedContent: {
                    contentType: contentType,
                    content: content
                }
            };
        }

        const rewritten = rewriteRefsWithUrls(parsed, indexedResolvedUrls);
        const rewrittenContent = JSON.stringify(rewritten, null, 2);

        return {
            typedContent: {
                contentType: 'application/json',
                content: rewrittenContent
            }
        };
    } catch {
        return {
            typedContent: {
                contentType: contentType,
                content: content
            }
        };
    }
}

/**
 * Rewrite $ref values to use resolved URLs
 */
function rewriteRefsWithUrls(obj: unknown, indexedUrls: Record<string, string>): unknown {
    if (obj === null || typeof obj !== 'object') {
        return obj;
    }
    if (Array.isArray(obj)) {
        return obj.map(item => rewriteRefsWithUrls(item, indexedUrls));
    }
    const record = obj as Record<string, unknown>;
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(record)) {
        if (key === '$ref' && typeof record[key] === 'string') {
            const ref = record[key] as string;
            if (!ref.startsWith('#') && indexedUrls[ref]) {
                result[key] = indexedUrls[ref];
            } else {
                result[key] = ref;
            }
        } else {
            result[key] = rewriteRefsWithUrls(record[key], indexedUrls);
        }
    }
    return result;
}

/**
 * Validate that all references are properly mapped
 */
export function validateReferences(request: ContentValidatorRequest): ContentValidatorResponse {
    const violations: Array<{ description: string; context: string | null }> = [];
    const mappedReferences: string[] = request.resolvedReferences || [];

    const foundReferences = findExternalReferences({
        typedContent: request.content
    });

    for (const ref of foundReferences.externalReferences) {
        if (!mappedReferences.includes(ref)) {
            violations.push({
                description: `Reference '${ref}' is not mapped`,
                context: null
            });
        }
    }

    return { ruleViolations: violations };
}
