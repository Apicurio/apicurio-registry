import axios from "axios";
import { ReconciledVariable } from "./promptTemplateVariables";

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

const isEmptyValue = (value: unknown): boolean => {
    return value === "" || value === undefined || value === null;
};

export const hasAllRequiredValues = (
    reconciledVariables: ReconciledVariable[],
    values: Record<string, any>
): boolean => {
    return reconciledVariables.every((entry) => {
        if (!entry.schema?.required) {
            return true;
        }
        return !isEmptyValue(values[entry.name]);
    });
};

export const isAbortError = (err: unknown): boolean => {
    if (axios.isCancel(err)) {
        return true;
    }
    const name = (err as { name?: string } | null | undefined)?.name;
    return name === "AbortError" || name === "CanceledError";
};
