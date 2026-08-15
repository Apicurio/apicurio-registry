export type ValidType = "default" | "success" | "error";

export type ArtifactReferenceFormItem = {
    groupId: string;
    artifactId: string;
    version: string;
    name: string;
};

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
 * Note: Explicitly rejects newlines (\n, \r) because JS regex $ matches before a trailing newline.
 */
export const checkVersionValid = (version: string | undefined | null): boolean => {
    if (!version) {
        return true;
    }
    if (version.includes("\n") || version.includes("\r")) {
        return false;
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

export const validateRefIdField = (value: string): ValidType => {
    if (value === "" || !checkIdValid(value)) {
        return "error";
    }
    return "success";
};

export const validateRefVersionField = (value: string): ValidType => {
    if (value === "" || !checkVersionValid(value)) {
        return "error";
    }
    return "success";
};

/**
 * Returns true if all reference rows have valid populated fields.
 * Returns true if there are no references (empty list is valid).
 */
export const isReferencesValid = (items: ArtifactReferenceFormItem[]): boolean => {
    return items.every(item =>
        item.name !== "" && checkIdValid(item.name) &&
        item.groupId !== "" && checkIdValid(item.groupId) &&
        item.artifactId !== "" && checkIdValid(item.artifactId) &&
        item.version !== "" && checkVersionValid(item.version)
    );
};

