import {
    extractTemplateVariableNames,
    reconcileTemplateVariables,
    ReconciledVariable,
    VariableSchema
} from "./promptTemplateVariables";

export type PromptVariableLike = VariableSchema;

export const toDeclaredMap = (
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined
): Record<string, VariableSchema> | undefined => {
    if (!variables) {
        return undefined;
    }
    if (Array.isArray(variables)) {
        const map: Record<string, VariableSchema> = {};
        for (const variable of variables) {
            if (variable.name) {
                map[variable.name] = variable;
            }
        }
        return map;
    }
    return variables;
};

export const defaultSchemaForDetected = (): VariableSchema => ({
    type: "string",
    required: false
});

export const schemaForField = (entry: ReconciledVariable): VariableSchema => {
    return entry.schema ?? defaultSchemaForDetected();
};

/**
 * Initial textarea text for an object/array variable, derived from its declared default.
 * The test panel keeps the raw text as the source of truth for these fields so keystrokes
 * are never re-formatted mid-type once the input happens to parse as valid JSON.
 *
 * Rules:
 *   undefined   -> ""      (no default declared)
 *   null        -> "null"  (explicit null default; preserved verbatim)
 *   string      -> as-is   (user-typed or default already stored as text)
 *   object/etc  -> pretty JSON
 */
export const initialObjectText = (value: unknown): string => {
    if (value === undefined) return "";
    if (value === null) return "null";
    if (typeof value === "string") return value;
    return JSON.stringify(value, null, 2);
};

export type TestPanelInitialState = {
    values: Record<string, any>;
    rawTexts: Record<string, string>;
};

/**
 * Build the initial values and rawTexts maps from a single reconciliation pass, so the
 * two can never drift apart. values covers every variable; rawTexts only carries entries
 * for object/array fields, where the textarea's raw text is the source of truth.
 */
export const buildInitialPanelState = (
    template: string | undefined,
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined
): TestPanelInitialState => {
    const reconciled = reconcileTemplateVariables(
        extractTemplateVariableNames(template || ""),
        toDeclaredMap(variables)
    );
    const values: Record<string, any> = {};
    const rawTexts: Record<string, string> = {};
    reconciled.forEach((entry) => {
        const schema = schemaForField(entry);
        const type = (schema.type || "string").toLowerCase();
        if (schema.default !== undefined) {
            values[entry.name] = schema.default;
        } else {
            values[entry.name] = type === "boolean" ? false : "";
        }
        if (type === "object" || type === "array") {
            rawTexts[entry.name] = initialObjectText(schema.default);
        }
    });
    return { values, rawTexts };
};

/** Values half of buildInitialPanelState, kept for callers/tests that only need one map. */
export const buildInitialValues = (
    template: string | undefined,
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined
): Record<string, any> => {
    return buildInitialPanelState(template, variables).values;
};

/** RawTexts half of buildInitialPanelState, kept for callers/tests that only need one map. */
export const buildInitialRawTexts = (
    template: string | undefined,
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined
): Record<string, string> => {
    return buildInitialPanelState(template, variables).rawTexts;
};

/**
 * Interpret the raw text of an object/array field. Valid JSON yields the parsed value;
 * anything else yields the raw string (the backend rejects it with a type validation
 * error on Render) plus a parseError flag the UI can surface. Empty text is not an
 * error: it just means the field is untouched.
 */
export const parseObjectInput = (text: string): { value: any; parseError: boolean } => {
    try {
        return { value: JSON.parse(text), parseError: false };
    } catch {
        return { value: text, parseError: text.trim() !== "" };
    }
};

/**
 * Whether the test panel state should be re-initialized. True when the viewed version
 * changed, or when the artifact content transitioned from absent to present (async
 * load after mount). Deliberately NOT true for a mere reference change of already
 * present content, so a parent that fails to memoize props cannot wipe user input.
 */
export const shouldResetPanelState = (
    versionChanged: boolean,
    contentPresent: boolean,
    contentWasPresent: boolean
): boolean => {
    return versionChanged || (contentPresent && !contentWasPresent);
};

export const coerceEnumValue = (val: string, type: string): any => {
    if (val === "") return "";
    switch (type) {
        case "integer":
            return parseInt(val, 10);
        case "number":
            return parseFloat(val);
        case "boolean":
            return val === "true";
        default:
            return val;
    }
};

/** Stable key for the artifact version currently shown in the Test Prompt panel. */
export const buildVersionIdentity = (groupId: string, artifactId: string, version: string): string => {
    return `${groupId}::${artifactId}::${version}`;
};

/**
 * Whether an async Render response should be applied.
 * Rejects stale responses after a newer Render starts or the viewed version changes.
 */
export const shouldAcceptRenderResponse = (
    requestId: number,
    latestRequestId: number,
    requestVersionIdentity: string,
    currentVersionIdentity: string
): boolean => {
    return requestId === latestRequestId && requestVersionIdentity === currentVersionIdentity;
};
