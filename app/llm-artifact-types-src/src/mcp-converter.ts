/**
 * MCP (Model Context Protocol) Converter Utility
 *
 * This utility converts PROMPT_TEMPLATE artifacts to MCP prompt format.
 * It auto-derives arguments from template variables when not explicitly defined.
 *
 * @see https://modelcontextprotocol.io/specification/2025-06-18/server/prompts
 */

import { parseContent } from './shared-utils';

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

interface MCPArgument {
    name: string;
    description?: string;
    required?: boolean;
}

interface MCPConfiguration {
    enabled?: boolean;
    name?: string;
    description?: string;
    arguments?: MCPArgument[];
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
    mcp?: MCPConfiguration;
}

/**
 * MCP Prompt representation as per the MCP specification
 */
export interface MCPPrompt {
    name: string;
    description?: string;
    arguments?: MCPPromptArgument[];
}

export interface MCPPromptArgument {
    name: string;
    description?: string;
    required?: boolean;
}

/**
 * MCP GetPromptResult as per the MCP specification
 */
export interface MCPGetPromptResult {
    description?: string;
    messages: MCPPromptMessage[];
}

export interface MCPPromptMessage {
    role: 'user' | 'assistant';
    content: MCPContent;
}

export type MCPContent = MCPTextContent | MCPImageContent | MCPEmbeddedResource;

export interface MCPTextContent {
    type: 'text';
    text: string;
}

export interface MCPImageContent {
    type: 'image';
    data: string;
    mimeType: string;
}

export interface MCPEmbeddedResource {
    type: 'resource';
    resource: {
        uri: string;
        mimeType?: string;
        text?: string;
        blob?: string;
    };
}

/**
 * Check if a prompt template is MCP-enabled
 */
export function isMCPEnabled(template: PromptTemplate): boolean {
    return template.mcp?.enabled === true;
}

/**
 * Convert a PROMPT_TEMPLATE to MCP Prompt format for listing
 *
 * @param template The prompt template to convert
 * @returns MCP Prompt object or null if MCP is not enabled
 */
export function toMCPPrompt(template: PromptTemplate): MCPPrompt | null {
    if (!isMCPEnabled(template)) {
        return null;
    }

    const mcpConfig = template.mcp!;

    // Derive arguments from variables if not explicitly specified
    const arguments_ = mcpConfig.arguments ?? deriveArgumentsFromVariables(template.variables);

    return {
        name: mcpConfig.name ?? template.templateId,
        description: mcpConfig.description ?? template.description,
        arguments: arguments_.length > 0 ? arguments_ : undefined
    };
}

/**
 * Derive MCP arguments from template variables
 */
function deriveArgumentsFromVariables(variables?: Record<string, VariableSchema>): MCPPromptArgument[] {
    if (!variables) {
        return [];
    }

    return Object.entries(variables).map(([name, schema]) => ({
        name,
        description: schema.description,
        required: schema.required
    }));
}

/**
 * Render a prompt template with the provided arguments
 *
 * @param template The prompt template to render
 * @param args The arguments to substitute into the template
 * @returns The rendered prompt content
 */
export function renderTemplate(template: string, args: Record<string, unknown>): string {
    let rendered = template;

    // Simple {{variable}} substitution
    for (const [key, value] of Object.entries(args)) {
        const placeholder = new RegExp(`\\{\\{${key}\\}\\}`, 'g');
        rendered = rendered.replace(placeholder, String(value ?? ''));
    }

    // Handle {{#if variable}} ... {{/if}} blocks
    rendered = processConditionalBlocks(rendered, args);

    return rendered;
}

/**
 * Process {{#if variable}} ... {{/if}} conditional blocks
 */
function processConditionalBlocks(template: string, args: Record<string, unknown>): string {
    const ifRegex = /\{\{#if\s+(\w+)\}\}([\s\S]*?)\{\{\/if\}\}/g;

    return template.replace(ifRegex, (_, varName, content) => {
        const value = args[varName];
        // Truthy check: non-null, non-undefined, non-empty string, non-false
        if (value !== null && value !== undefined && value !== '' && value !== false) {
            return content;
        }
        return '';
    });
}

/**
 * Convert a PROMPT_TEMPLATE to MCP GetPromptResult format
 *
 * @param template The prompt template to convert
 * @param args The arguments to substitute into the template
 * @returns MCP GetPromptResult object or null if MCP is not enabled
 */
export function toMCPGetPromptResult(
    template: PromptTemplate,
    args: Record<string, unknown>
): MCPGetPromptResult | null {
    if (!isMCPEnabled(template)) {
        return null;
    }

    const mcpConfig = template.mcp!;
    const renderedContent = renderTemplate(template.template, args);

    return {
        description: mcpConfig.description ?? template.description,
        messages: [
            {
                role: 'user',
                content: {
                    type: 'text',
                    text: renderedContent
                }
            }
        ]
    };
}

/**
 * Parse content and convert to MCP Prompt format
 *
 * @param content The raw content string (JSON or YAML)
 * @param contentType The content type (application/json or application/x-yaml)
 * @returns MCP Prompt object or null if not valid or MCP not enabled
 */
export function parseAndConvertToMCPPrompt(content: string, contentType: string): MCPPrompt | null {
    const parsed = parseContent<PromptTemplate>(content, contentType);
    if (!parsed) {
        return null;
    }
    return toMCPPrompt(parsed);
}

/**
 * Parse content and convert to MCP GetPromptResult format
 *
 * @param content The raw content string (JSON or YAML)
 * @param contentType The content type (application/json or application/x-yaml)
 * @param args The arguments to substitute into the template
 * @returns MCP GetPromptResult object or null if not valid or MCP not enabled
 */
export function parseAndConvertToMCPGetPromptResult(
    content: string,
    contentType: string,
    args: Record<string, unknown>
): MCPGetPromptResult | null {
    const parsed = parseContent<PromptTemplate>(content, contentType);
    if (!parsed) {
        return null;
    }
    return toMCPGetPromptResult(parsed, args);
}
