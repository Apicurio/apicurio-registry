export type ValidType = "default" | "success" | "error";

export const checkIdValid = (id: string | undefined | null): boolean => {
    if (!id) {
        return true;
    }
    return /^[a-zA-Z0-9._+:,-]+$/.test(id);
};

export const validateField = (value: string | undefined | null): ValidType => {
    if (!checkIdValid(value)) {
        return "error";
    }
    if (value === undefined || value === null || value === "") {
        return "default";
    }
    return "success";
};

