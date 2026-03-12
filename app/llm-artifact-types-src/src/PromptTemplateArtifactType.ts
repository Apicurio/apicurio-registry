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
import { stringify as stringifyYaml } from 'yaml';
import {
    TypedContent,
    parseContent,
    sortObjectKeys,
    findRefs,
    resolveRefs,
    rewriteRefsWithUrls
} from './shared-utils';

interface VariableSchema {
    type?: string;
    required?: boolean;
    default?: unknown;
    description?: string;
    enum?: unknown[];
    minimum?: number;
    maximum?: number;
    $ref?: string;
}

/**
 * MCP (Model Context Protocol) extension for exposing prompt templates via MCP servers.
 * When enabled, this prompt template can be served via MCP prompts/list and prompts/get.
 *
 * @see https://modelcontextprotocol.io/specification/2025-06-18/server/prompts
 */
interface MCPExtension {
    /** Whether to expose this template via MCP prompts/list and prompts/get */
    enabled: boolean;
    /** MCP prompt name (lowercase with underscores). Defaults to templateId if not specified. */
    name?: string;
    /** Description shown in MCP prompts/list. Defaults to template description if not specified. */
    description?: string;
    /** Explicit MCP arguments. If not specified, auto-derived from variables. */
    arguments?: Array<{
        name: string;
        description?: string;
        required?: boolean;
    }>;
}

interface PromptTemplate {
    $schema?: string;
    templateId: string;
    name?: string;
    description?: string;
    version?: string;
    template: string;
    variables?: Record<string, VariableSchema>;
    outputSchema?: Record<string, unknown>;
    metadata?: Record<string, unknown>;
    /** Optional MCP extension for runtime serving via MCP servers */
    mcp?: MCPExtension;
}

/**
 * Extract variable names from template content using {{variable}} syntax
 */
function extractTemplateVariables(template: string): string[] {
    const regex = /\{\{(\w+)\}\}/g;
    const variables: string[] = [];
    let match;
    while ((match = regex.exec(template)) !== null) {
        if (!variables.includes(match[1])) {
            variables.push(match[1]);
        }
    }
    return variables;
}

/**
 * Check if the content is a PROMPT_TEMPLATE artifact type
 */
export function acceptsContent(request: ContentAccepterRequest): boolean {
    const content: string = request.typedContent.content;
    const contentType: string = request.typedContent.contentType;

    // Accept JSON, YAML, or prompt template content types
    if (contentType !== 'application/json' &&
        contentType !== 'application/x-yaml' &&
        contentType !== 'application/yaml' &&
        contentType !== 'text/x-prompt-template') {
        return false;
    }

    const parsed = parseContent<PromptTemplate>(content, contentType);
    if (!parsed) {
        return false;
    }

    // Check for $schema containing 'prompt-template'
    if (parsed.$schema && parsed.$schema.includes('prompt-template')) {
        return true;
    }

    // Check for presence of templateId + template
    if (parsed.templateId && parsed.template) {
        return true;
    }

    return false;
}

/**
 * Validate PROMPT_TEMPLATE content structure
 */
export function validate(request: ContentValidatorRequest): ContentValidatorResponse {
    const violations: Array<{ description: string; context: string | null }> = [];
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    // Validate content type
    if (contentType !== 'application/json' &&
        contentType !== 'application/x-yaml' &&
        contentType !== 'application/yaml' &&
        contentType !== 'text/x-prompt-template') {
        violations.push({
            description: `Incorrect content type. Expected 'application/json', 'application/x-yaml', or 'text/x-prompt-template' but found '${contentType}'.`,
            context: null
        });
        return { ruleViolations: violations };
    }

    // Parse content
    const parsed = parseContent<PromptTemplate>(content, contentType);
    if (!parsed) {
        violations.push({
            description: 'Invalid content format. Unable to parse as JSON or YAML.',
            context: null
        });
        return { ruleViolations: violations };
    }

    // Validate required field: templateId
    if (!parsed.templateId || typeof parsed.templateId !== 'string') {
        violations.push({
            description: "Missing or invalid required field 'templateId'. Must be a non-empty string.",
            context: '/templateId'
        });
    }

    // Validate required field: template
    if (!parsed.template || typeof parsed.template !== 'string') {
        violations.push({
            description: "Missing or invalid required field 'template'. Must be a non-empty string.",
            context: '/template'
        });
        return { ruleViolations: violations };
    }

    // Extract variables from template
    const templateVariables = extractTemplateVariables(parsed.template);
    const definedVariables = parsed.variables ? Object.keys(parsed.variables) : [];

    // Check that all template variables are defined
    for (const variable of templateVariables) {
        if (!definedVariables.includes(variable)) {
            violations.push({
                description: `Template variable '{{${variable}}}' is used but not defined in 'variables' schema.`,
                context: `/variables/${variable}`
            });
        }
    }

    // Validate variable definitions
    if (parsed.variables) {
        for (const [varName, varSchema] of Object.entries(parsed.variables)) {
            // Validate type if specified
            if (varSchema.type !== undefined) {
                const validTypes = ['string', 'integer', 'number', 'boolean', 'array', 'object'];
                if (!validTypes.includes(varSchema.type)) {
                    violations.push({
                        description: `Variable '${varName}' has invalid type '${varSchema.type}'. Must be one of: ${validTypes.join(', ')}.`,
                        context: `/variables/${varName}/type`
                    });
                }
            }

            // Validate numeric constraints
            if (varSchema.minimum !== undefined && typeof varSchema.minimum !== 'number') {
                violations.push({
                    description: `Variable '${varName}' has invalid 'minimum' value. Must be a number.`,
                    context: `/variables/${varName}/minimum`
                });
            }
            if (varSchema.maximum !== undefined && typeof varSchema.maximum !== 'number') {
                violations.push({
                    description: `Variable '${varName}' has invalid 'maximum' value. Must be a number.`,
                    context: `/variables/${varName}/maximum`
                });
            }

            // Validate enum is an array if present
            if (varSchema.enum !== undefined && !Array.isArray(varSchema.enum)) {
                violations.push({
                    description: `Variable '${varName}' has invalid 'enum' value. Must be an array.`,
                    context: `/variables/${varName}/enum`
                });
            }
        }
    }

    // Validate outputSchema structure if present
    if (parsed.outputSchema !== undefined && typeof parsed.outputSchema !== 'object') {
        violations.push({
            description: "Field 'outputSchema' must be an object if provided.",
            context: '/outputSchema'
        });
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
 * Test compatibility between existing and proposed PROMPT_TEMPLATE artifacts
 * BACKWARD compatibility rules:
 * - Cannot remove variables used in the template
 * - Cannot change variable types
 * - Cannot make optional variables required
 * - Can add new optional variables
 * - Template content changes are allowed if variables remain compatible
 */
export function testCompatibility(request: CompatibilityCheckerRequest): CompatibilityCheckerResponse {
    const existingArtifacts: TypedContent[] = request.existingArtifacts;
    const proposedArtifact: TypedContent = request.proposedArtifact;

    if (!existingArtifacts || existingArtifacts.length === 0) {
        return { incompatibleDifferences: [] };
    }

    const incompatibleDifferences: Array<{ description: string; context: string }> = [];

    try {
        const proposed = parseContent<PromptTemplate>(proposedArtifact.content, proposedArtifact.contentType);
        const existing = parseContent<PromptTemplate>(existingArtifacts[0].content, existingArtifacts[0].contentType);

        if (!proposed || !existing) {
            return {
                incompatibleDifferences: [{
                    description: 'Unable to parse content for compatibility check',
                    context: '/'
                }]
            };
        }

        // Get existing and proposed variables
        const existingVars = existing.variables || {};
        const proposedVars = proposed.variables || {};

        // Get template variables from the proposed template
        const proposedTemplateVars = extractTemplateVariables(proposed.template);

        // Check for removed variables that are still used in template
        for (const varName of Object.keys(existingVars)) {
            if (!proposedVars[varName]) {
                // Variable was removed - check if it's still used in proposed template
                if (proposedTemplateVars.includes(varName)) {
                    incompatibleDifferences.push({
                        description: `Variable '${varName}' was removed but is still used in the template.`,
                        context: `/variables/${varName}`
                    });
                }
            }
        }

        // Check for type changes
        for (const varName of Object.keys(existingVars)) {
            if (proposedVars[varName]) {
                const existingType = existingVars[varName].type;
                const proposedType = proposedVars[varName].type;

                if (existingType && proposedType && existingType !== proposedType) {
                    incompatibleDifferences.push({
                        description: `Variable '${varName}' type changed from '${existingType}' to '${proposedType}'.`,
                        context: `/variables/${varName}/type`
                    });
                }

                // Check if optional variable became required
                const wasRequired = existingVars[varName].required === true;
                const isRequired = proposedVars[varName].required === true;

                if (!wasRequired && isRequired) {
                    incompatibleDifferences.push({
                        description: `Variable '${varName}' changed from optional to required.`,
                        context: `/variables/${varName}/required`
                    });
                }

                // Check for narrowed enum values
                const existingEnum = existingVars[varName].enum;
                const proposedEnum = proposedVars[varName].enum;

                if (existingEnum && proposedEnum && Array.isArray(existingEnum) && Array.isArray(proposedEnum)) {
                    for (const val of existingEnum) {
                        if (!proposedEnum.includes(val)) {
                            incompatibleDifferences.push({
                                description: `Variable '${varName}' enum value '${val}' was removed.`,
                                context: `/variables/${varName}/enum`
                            });
                        }
                    }
                }
            }
        }

        // Check outputSchema compatibility - cannot remove properties
        if (existing.outputSchema && proposed.outputSchema) {
            const existingProps = (existing.outputSchema as { properties?: Record<string, unknown> }).properties || {};
            const proposedProps = (proposed.outputSchema as { properties?: Record<string, unknown> }).properties || {};

            for (const propName of Object.keys(existingProps)) {
                if (!proposedProps[propName]) {
                    incompatibleDifferences.push({
                        description: `Output schema property '${propName}' was removed.`,
                        context: `/outputSchema/properties/${propName}`
                    });
                }
            }
        } else if (existing.outputSchema && !proposed.outputSchema) {
            incompatibleDifferences.push({
                description: "Output schema was removed.",
                context: '/outputSchema'
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
 * Canonicalize PROMPT_TEMPLATE content by normalizing YAML structure
 */
export function canonicalize(request: ContentCanonicalizerRequest): ContentCanonicalizerResponse {
    const content: string = request.content.content;
    const contentType: string = request.content.contentType;

    try {
        const parsed = parseContent<PromptTemplate>(content, contentType);
        if (!parsed) {
            return {
                typedContent: {
                    contentType: contentType,
                    content: content
                }
            };
        }

        // Sort keys recursively (except for 'template' which preserves its string)
        const sorted = sortObjectKeys(parsed);

        // Output as YAML for canonical form (preserves template multiline strings better)
        const canonicalContent = stringifyYaml(sorted, {
            lineWidth: 0,
            defaultKeyType: 'PLAIN',
            defaultStringType: 'QUOTE_DOUBLE'
        });

        return {
            typedContent: {
                contentType: 'application/x-yaml',
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
 * Find external references in the PROMPT_TEMPLATE content
 */
export function findExternalReferences(request: ReferenceFinderRequest): ReferenceFinderResponse {
    const content: string = request.typedContent.content;
    const contentType: string = request.typedContent.contentType;

    const references: string[] = [];

    try {
        const parsed = parseContent<PromptTemplate>(content, contentType);
        if (parsed) {
            // Look for $ref in variables and outputSchema
            if (parsed.variables) {
                findRefs(parsed.variables, references);
            }
            if (parsed.outputSchema) {
                findRefs(parsed.outputSchema, references);
            }
        }
    } catch {
        // Return empty references on parse error
    }

    return { externalReferences: references };
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
        const parsed = parseContent<PromptTemplate>(content, contentType);
        if (!parsed) {
            return {
                typedContent: {
                    contentType: contentType,
                    content: content
                }
            };
        }

        const dereferenced = resolveRefs(parsed, indexedResolvedRefs, parseContent<PromptTemplate>);
        const dereferencedContent = stringifyYaml(dereferenced, {
            lineWidth: 0,
            defaultKeyType: 'PLAIN',
            defaultStringType: 'QUOTE_DOUBLE'
        });

        return {
            typedContent: {
                contentType: 'application/x-yaml',
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
        const parsed = parseContent<PromptTemplate>(content, contentType);
        if (!parsed) {
            return {
                typedContent: {
                    contentType: contentType,
                    content: content
                }
            };
        }

        const rewritten = rewriteRefsWithUrls(parsed, indexedResolvedUrls);
        const rewrittenContent = stringifyYaml(rewritten, {
            lineWidth: 0,
            defaultKeyType: 'PLAIN',
            defaultStringType: 'QUOTE_DOUBLE'
        });

        return {
            typedContent: {
                contentType: 'application/x-yaml',
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
