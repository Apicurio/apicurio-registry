export const isPropertyInputValid = (type: "text" | "number", value: string): boolean => {
    if (type === "text") {
        return value.trim().length > 0;
    } else if (type === "number") {
        if (value.trim().length === 0) {
            return false;
        }
        const num: number = Number(value);
        return Number.isInteger(num) && num >= 0;
    }
    return true;
};
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
