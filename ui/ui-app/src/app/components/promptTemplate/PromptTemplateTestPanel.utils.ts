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
