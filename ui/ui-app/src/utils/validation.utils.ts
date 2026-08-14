export type ValidType = "default" | "success" | "error";

/**
 * Validates group ID, artifact ID, or reference name against backend rules (ArtifactIdValidator.java):
 * Any ASCII character except '%', max 512 characters.
 */
export const checkIdValid = (id: string | undefined | null): boolean => {
    if (!id) {
        return true;
    }
    if (id.length > 512) {
        return false;
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

/**
 * Validates version string against backend rules (VersionId.java):
 * Regex: [a-zA-Z0-9._\-+]{1,256}
 */
export const checkVersionValid = (version: string | undefined | null): boolean => {
    if (!version) {
        return true;
    }
    const VERSION_REGEX = /^[a-zA-Z0-9._\-+]{1,256}$/;
    return VERSION_REGEX.test(version);
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

export const validateVersionField = (value: string | undefined | null): ValidType => {
    if (!checkVersionValid(value)) {
        return "error";
    }
    if (value === undefined || value === null || value === "") {
        return "default";
    }
    return "success";
};
