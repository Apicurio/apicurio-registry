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
