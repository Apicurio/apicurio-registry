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
 * Build a fresh values map from template-detected names + declared variable schemas.
 * Used on initial mount and whenever the viewed version identity changes.
 */
export const buildInitialValues = (
    template: string | undefined,
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined
): Record<string, any> => {
    const reconciled = reconcileTemplateVariables(
        extractTemplateVariableNames(template || ""),
        toDeclaredMap(variables)
    );
    const values: Record<string, any> = {};
    reconciled.forEach((entry) => {
        const schema = schemaForField(entry);
        if (schema.default !== undefined) {
            values[entry.name] = schema.default;
        } else {
            values[entry.name] = (schema.type || "string").toLowerCase() === "boolean" ? false : "";
        }
    });
    return values;
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

/**
 * Build the initial rawTexts map for object/array fields.
 * Non-object/array fields are omitted; the standard values map covers them.
 */
export const buildInitialRawTexts = (
    template: string | undefined,
    variables: Record<string, VariableSchema> | VariableSchema[] | undefined
): Record<string, string> => {
    const reconciled = reconcileTemplateVariables(
        extractTemplateVariableNames(template || ""),
        toDeclaredMap(variables)
    );
    const rawTexts: Record<string, string> = {};
    reconciled.forEach((entry) => {
        const schema = schemaForField(entry);
        const type = (schema.type || "string").toLowerCase();
        if (type === "object" || type === "array") {
            rawTexts[entry.name] = initialObjectText(schema.default);
        }
    });
    return rawTexts;
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
