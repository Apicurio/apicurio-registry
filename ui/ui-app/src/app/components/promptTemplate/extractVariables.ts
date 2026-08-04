/**
 * Handlebars keywords that must never be treated as data variables.
 *
 * - Block prefixes (if/unless/each/with) are already caught by the `^[#/]`
 *   prefix check, but included here for completeness if they somehow appear
 *   without a prefix.
 * - `else` and `this` appear as plain `{{else}}` / `{{this}}` — no prefix —
 *   so they slip past the prefix check. This was flagged by paoloantinori
 *   on the parallel PR.
 * - `lookup` and `log` are built-in Handlebars helpers, not data variables.
 */
const BLOCK_KEYWORDS = new Set([
    "if", "unless", "each", "with",
    "else", "this",
    "lookup", "log",
]);

/**
 * Canonical regex pattern matching {{variable}} tokens in template strings.
 */
export const VARIABLE_PATTERN = /\{\{([^}]+)\}\}/g;

/**
 * Extracts {{variable}} names from a prompt template string.
 *
 * Handles:
 * - Standard syntax: {{name}}
 * - Whitespace: {{ name }}
 * - Deduplication: {{name}} ... {{name}} → ["name"]
 * - Handlebars block helpers: {{#if x}}, {{/if}} are excluded (not variables)
 * - Handlebars keywords: {{else}}, {{this}}, {{lookup}}, {{log}} are excluded
 * - Malformed/empty: {{ }} → skipped
 *
 * @param template - The template string to extract variables from
 * @returns Deduplicated array of variable names, in order of first appearance
 */
export const extractVariables = (template: string): string[] => {
    if (!template) {
        return [];
    }

    const variablePattern = new RegExp(VARIABLE_PATTERN.source, "g");
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

        // Skip Handlebars keywords: {{else}}, {{this}}, {{lookup}}, {{log}}
        if (BLOCK_KEYWORDS.has(raw)) {
            continue;
        }

        if (!seen.has(raw)) {
            seen.add(raw);
            result.push(raw);
        }
    }

    return result;
};
