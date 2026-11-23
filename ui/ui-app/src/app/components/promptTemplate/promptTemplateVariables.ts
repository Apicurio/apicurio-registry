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
 * The template syntax the backend supports, as one pattern:
 * - group 1 is the subject of a conditional block opener, as matched by
 *   `PromptRenderingService`: `{{#if name}}` or `{{#unless name}}`. Spaced forms
 *   such as `{{ #if name }}` are not valid blocks.
 * - group 2 is a substitution placeholder, as matched by
 *   `PromptTemplateVariableUtil.VARIABLE_PATTERN`: `{{name}}`, with optional
 *   whitespace inside the braces (`{{ name }}`).
 *
 * The placeholder's leading `(?<!\{)` mirrors the backend: without it, `{{{name}}}`
 * still contains `{{name}}` starting at the second character, so the UI would show
 * a `name` input whose value the backend ignores. Like the backend, there is no
 * trailing `(?!\})`: `{{name}}}` is a placeholder followed by a literal brace.
 *
 * Deliberate scope limitations (matching the backend):
 * - Names are `\w+` only, so dotted paths like `{{user.name}}` are not variables.
 * - `@index`-style tokens, helper expressions such as `{{lookup obj key}}` and
 *   `{{#each}}` / `{{#with}}` blocks name no variable.
 */
const TEMPLATE_REFERENCE_REGEX = /\{\{#(?:if|unless)\s+(\w+)\}\}|(?<!\{)\{\{\s*(\w+)\s*\}\}/g;

/**
 * Bare tag names the backend never substitutes (`PromptTemplateVariableUtil`).
 * Other helper names written bare, such as `{{lookup}}` or `{{log}}`, are
 * ordinary variables there, so they are here too.
 */
const CONTROL_KEYWORDS = new Set(["else", "this"]);

/** How a template uses one name. */
export type TemplateVariableReference = {
    name: string;
    /** Substituted somewhere as a placeholder, e.g. `{{name}}`. */
    placeholder: boolean;
    /** Tested somewhere as the subject of `{{#if name}}` or `{{#unless name}}`. */
    condition: boolean;
};

/**
 * Extract de-duplicated variable references from template text in first-seen
 * order, recording for each name whether it is a placeholder, a condition
 * subject, or both.
 */
export function extractTemplateVariableReferences(template: string): TemplateVariableReference[] {
    if (!template) {
        return [];
    }

    const references = new Map<string, TemplateVariableReference>();
    const regex = new RegExp(TEMPLATE_REFERENCE_REGEX.source, TEMPLATE_REFERENCE_REGEX.flags);
    let match: RegExpExecArray | null;

    while ((match = regex.exec(template)) !== null) {
        const condition = match[1] !== undefined;
        const name = condition ? match[1] : match[2];
        if (!condition && CONTROL_KEYWORDS.has(name)) {
            continue;
        }
        let reference = references.get(name);
        if (!reference) {
            reference = { name, placeholder: false, condition: false };
            references.set(name, reference);
        }
        if (condition) {
            reference.condition = true;
        } else {
            reference.placeholder = true;
        }
    }

    return Array.from(references.values());
}

/**
 * Extract de-duplicated variable names from template text in first-seen order:
 * placeholders and condition subjects alike, so every input the template reads
 * is available (e.g. in the Test Prompt panel).
 */
export function extractTemplateVariableNames(template: string): string[] {
    return extractTemplateVariableReferences(template).map(reference => reference.name);
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
