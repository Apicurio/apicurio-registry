import { PromptVariable } from "./promptTemplate.types";

export const getVariablesList = (variables: Record<string, PromptVariable> | PromptVariable[] | undefined): { name: string; variable: PromptVariable }[] => {
    if (!variables) return [];
    if (Array.isArray(variables)) {
        return variables.map(v => ({ name: v.name || "", variable: v }));
    }
    return Object.entries(variables).map(([name, variable]) => ({ name, variable }));
};

// Format a variable default for display in the Variables table.
// Objects and arrays go through JSON.stringify so they don't render as "[object Object]".
export const formatDefault = (value: any): string => {
    if (typeof value === "object" && value !== null) {
        return JSON.stringify(value);
    }
    return String(value);
};
