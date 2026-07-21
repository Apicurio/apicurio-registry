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
    return id.indexOf("%") === -1 && isAscii(id);
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
