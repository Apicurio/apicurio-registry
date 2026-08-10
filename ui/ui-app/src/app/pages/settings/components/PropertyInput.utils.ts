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
