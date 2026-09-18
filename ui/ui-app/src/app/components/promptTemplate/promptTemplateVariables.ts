/**
 * Shared prompt-template variable parsing and schema reconciliation.
 *
 * `VariableSchema` mirrors `$defs.variableSchema` in
 * `app/src/main/resources/schemas/prompt-template-v1.json`, plus optional `name`
 * for the UI's alternate array-shaped `variables` list (name is the map key in
 * the canonical object form).
 */
export type VariableSchema = {
    /** Present when `variables` is an array of entries instead of a name→schema map. */
    name?: string;
    type?: "string" | "integer" | "number" | "boolean" | "array" | "object" | string;
    required?: boolean;
    description?: string;
    default?: unknown;
    enum?: unknown[];
    minimum?: number;
    maximum?: number;
    $ref?: string;
};

export type ReconciledVariable = {
    name: string;
    schema: VariableSchema | undefined;
    source: "declared" | "detected" | "both";
};

/**
 * Shared placeholder matcher used by extractTemplateVariableNames and
 * PromptTemplateViewer token classification.
 *
 * Captures optional block prefix (group 1) and variable name (group 2).
 * Optional whitespace inside the braces matches the backend canonical plain-
 * variable pattern from PromptTemplateVariableUtil
 * (`\{\{\s*(\w+)\s*\}\}`), while still supporting handlebars-style blocks
 * (`{{#if x}}`, `{{ #if x }}`, etc.) in one expression for the UI.
 *
 * Deliberate scope limitations (matching backend #8977 / PromptTemplateVariableUtil):
 * - Names are `\w+` only — dotted paths like `{{user.name}}` and hyphenated
 *   names are NOT supported, same as the backend canonical pattern.
 * - Handlebars special iteration variables (`@index`, `@key`, `@first`,
 *   `@last`) are not recognized as variables (they start with `@`, outside
 *   `\w`), consistent with the backend not supporting them either. If the
 *   backend adds support later, this frontend logic will need a matching update.
 */
export const TEMPLATE_VARIABLE_REGEX = /\{\{\s*(#?\/?(?:if|unless|each|with)\s+)?(\w+)\s*\}\}/g;

/**
 * Bare tag names that are Handlebars syntax/helpers, not user variables.
 * Applied after capture so keywords that appear as the whole tag body
 * (e.g. `{{else}}`, `{{this}}`) are excluded the same way prefix keywords are.
 */
const BLOCK_KEYWORDS = new Set(["if", "unless", "each", "with", "else", "this", "lookup", "log"]);

export type HandlebarsTagKind = "block" | "variable" | "plain";

/**
 * Strip braces, trim, and remove Handlebars whitespace-control tildes
 * from the edges of a tag body.
 */
export const parseHandlebarsTagInner = (tag: string): { inner: string; head: string } => {
    const inner = tag.replace(/^\{+|\}+$/g, "").trim().replace(/^~+|~+$/g, "").trim();
    const head = inner.split(/\s+/, 1)[0];
    return { inner, head };
};

const isBlockHelperHead = (head: string): boolean => {
    return head.startsWith("#") || head.startsWith("/") || head.startsWith("^") || head === "else";
};

export const isBlockHelperTag = (tag: string): boolean => {
    return isBlockHelperHead(parseHandlebarsTagInner(tag).head);
};

const isSubstitutablePlainVariableTag = (tag: string): boolean => {
    const { inner } = parseHandlebarsTagInner(tag);
    if (!/^\w+$/.test(inner)) {
        return false;
    }
    return !BLOCK_KEYWORDS.has(inner);
};

/**
 * Classify a single handlebars tag for Template preview highlighting.
 * Mirrors extractTemplateVariableNames() / Test Prompt panel scope for variables.
 */
export function classifyHandlebarsTag(tag: string): HandlebarsTagKind {
    if (isBlockHelperTag(tag)) {
        return "block";
    }
    if (isSubstitutablePlainVariableTag(tag)) {
        return "variable";
    }
    return "plain";
}

/**
 * Whether a single handlebars tag is a substitutable variable per
 * extractTemplateVariableNames() / backend `\w+` scope.
 */
export function isSubstitutableVariableTag(tag: string): boolean {
    return classifyHandlebarsTag(tag) === "variable";
}

/**
 * Extract de-duplicated variable names from template text in first-seen order.
 * Block keywords (if/unless/each/with) are never returned as names.
 */
export function extractTemplateVariableNames(template: string): string[] {
    if (!template) {
        return [];
    }

    const seen = new Set<string>();
    const names: string[] = [];
    const regex = new RegExp(TEMPLATE_VARIABLE_REGEX.source, TEMPLATE_VARIABLE_REGEX.flags);
    let match: RegExpExecArray | null;

    while ((match = regex.exec(template)) !== null) {
        const name = match[2];
        if (!name || BLOCK_KEYWORDS.has(name)) {
            continue;
        }
        if (!seen.has(name)) {
            seen.add(name);
            names.push(name);
        }
    }

    return names;
}

/**
 * Reconcile template-detected names with the artifact's declared variables map.
 * Template order is preserved for detected/both; unused declared names follow.
 */
export function reconcileTemplateVariables(
    templateVarNames: string[],
    declaredVariables: Record<string, VariableSchema> | undefined
): ReconciledVariable[] {
    const declared = declaredVariables ?? {};
    const declaredNames = Object.keys(declared);
    const declaredSet = new Set(declaredNames);
    const result: ReconciledVariable[] = [];
    const seen = new Set<string>();

    for (const name of templateVarNames) {
        if (seen.has(name)) {
            continue;
        }
        seen.add(name);
        if (declaredSet.has(name)) {
            result.push({ name, schema: declared[name], source: "both" });
        } else {
            result.push({ name, schema: undefined, source: "detected" });
        }
    }

    for (const name of declaredNames) {
        if (seen.has(name)) {
            continue;
        }
        seen.add(name);
        result.push({ name, schema: declared[name], source: "declared" });
    }

    return result;
}
