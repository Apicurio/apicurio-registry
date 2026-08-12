export const LOG_LEVEL_OPTIONS: string[] = [
    "TRACE",
    "DEBUG",
    "INFO",
    "WARN",
    "ERROR",
    "OFF",
    "ALL"
];

export type ValidationResult = {
    isValid: boolean;
    errorMessage?: string;
};

export const validatePropertyValue = (
    value: string,
    type: "text" | "number",
    options?: string[]
): ValidationResult => {
    const trimmedValue = value.trim();

    if (trimmedValue.length === 0) {
        return {
            isValid: false,
            errorMessage: "Value cannot be empty"
        };
    }

    if (options && options.length > 0) {
        if (!options.includes(trimmedValue)) {
            return {
                isValid: false,
                errorMessage: `Value must be one of: ${options.join(", ")}`
            };
        }
        return { isValid: true };
    }

    if (type === "number" && !/^\d+$/.test(trimmedValue)) {
        return {
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        };
    }

    return { isValid: true };
};