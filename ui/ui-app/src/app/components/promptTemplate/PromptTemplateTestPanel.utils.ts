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


export const describeNumericRange = (
    minimum: number | undefined,
    maximum: number | undefined
): string | undefined => {
    if (minimum !== undefined && maximum !== undefined) {
        return `Must be between ${minimum} and ${maximum}`;
    }
    if (minimum !== undefined) {
        return `Must be at least ${minimum}`;
    }
    if (maximum !== undefined) {
        return `Must be at most ${maximum}`;
    }
    return undefined;
};

export type RangeCheckField = {
    name: string;
    type?: string;
    minimum?: number;
    maximum?: number;
};

export type RangeError = {
    variableName: string;
    message: string;
};

export const findOutOfRangeErrors = (
    fields: RangeCheckField[],
    values: Record<string, any>
): RangeError[] => {
    const errors: RangeError[] = [];
    fields.forEach((field) => {
        const type = (field.type || "string").toLowerCase();
        if (type !== "integer" && type !== "number") {
            return;
        }
        const val = values[field.name];
        if (typeof val !== "number") {
            return;
        }
        if (field.minimum !== undefined && val < field.minimum) {
            errors.push({ variableName: field.name, message: `Value must be at least ${field.minimum}` });
        }
        if (field.maximum !== undefined && val > field.maximum) {
            errors.push({ variableName: field.name, message: `Value must be at most ${field.maximum}` });
        }
    });
    return errors;
};
