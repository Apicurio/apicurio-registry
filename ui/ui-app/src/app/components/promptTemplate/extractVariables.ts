/**
 * Extracts {{variable}} names from a prompt template string.
 *
 * Handles:
 * - Standard syntax: {{name}}
 * - Whitespace: {{ name }}
 * - Deduplication: {{name}} ... {{name}} → ["name"]
 * - Handlebars block helpers: {{#if x}}, {{/if}} are excluded (not variables)
 * - Malformed/empty: {{ }} → skipped
 *
 * @param template - The template string to extract variables from
 * @returns Deduplicated array of variable names, in order of first appearance
 */
export const extractVariables = (template: string): string[] => {
    if (!template) {
        return [];
    }

    const variablePattern = /\{\{([^}]+)\}\}/g;
    const seen = new Set<string>();
    const result: string[] = [];
    let match: RegExpExecArray | null;

    while ((match = variablePattern.exec(template)) !== null) {
        const raw = match[1].trim();

        // Skip empty matches (e.g. {{ }})
        if (!raw) {
            continue;
        }

        // Skip Handlebars block syntax: {{#if ...}}, {{/if}}, {{#each ...}}, etc.
        if (/^[#/]/.test(raw)) {
            continue;
        }

        // Skip if it contains spaces (likely a block expression, not a variable)
        // e.g. "if condition" from {{#if condition}} that somehow lost the #
        if (/\s/.test(raw)) {
            continue;
        }

        if (!seen.has(raw)) {
            seen.add(raw);
            result.push(raw);
        }
    }

    return result;
};
