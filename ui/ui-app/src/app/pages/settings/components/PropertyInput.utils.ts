export type ValidationResult = {
    isValid: boolean;
    errorMessage?: string;
};

export const validatePropertyValue = (
    value: string,
    type: "text" | "number"
): ValidationResult => {
    const trimmedValue = value.trim();

    if (trimmedValue.length === 0) {
        return {
            isValid: false,
            errorMessage: "Value cannot be empty"
        };
    }

    if (type === "number" && !/^-?\d+$/.test(trimmedValue)) {
        return {
            isValid: false,
            errorMessage: "Value must be a number"
        };
    }

    return { isValid: true };
};