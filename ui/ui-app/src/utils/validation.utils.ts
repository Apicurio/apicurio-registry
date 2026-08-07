export type ValidType = "default" | "success" | "error";

export const checkIdValid = (id: string | undefined | null): boolean => {
    if (!id) {
        return true;
    }
    const isAscii = (str: string) => {
        for (let i = 0; i < str.length; i++) {
            if (str.charCodeAt(i) > 127) {
                return false;
            }
        }
        return true;
    };
    const hasInvalidChars = (str: string) => {
        // Disallow URL-unsafe and special characters that break REST API path routing or URI parsing
        return /[%/\\#?@$^&*()\s]/.test(str);
    };
    return !hasInvalidChars(id) && isAscii(id);
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

