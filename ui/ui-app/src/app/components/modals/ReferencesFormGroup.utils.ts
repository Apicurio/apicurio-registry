import { ArtifactReference } from "@sdk/lib/generated-client/models";
import { checkIdValid } from "@utils/validation.utils.ts";

export type ArtifactReferenceFormItem = {
    groupId: string;
    artifactId: string;
    version: string;
    name: string;
};

type ValidType = "default" | "success" | "error";

export const validateRefField = (value: string): ValidType => {
    if (value === "" || !checkIdValid(value)) {
        return "error";
    }
    return "success";
};

/**
 * Returns true if all reference rows have all four fields populated and valid.
 * Returns true if there are no references (empty list is valid).
 */
export const isReferencesValid = (items: ArtifactReferenceFormItem[]): boolean => {
    return items.every(item =>
        item.name !== "" && checkIdValid(item.name) &&
        item.groupId !== "" && checkIdValid(item.groupId) &&
        item.artifactId !== "" && checkIdValid(item.artifactId) &&
        item.version !== "" && checkIdValid(item.version)
    );
};

/**
 * Converts form items to SDK ArtifactReference objects.
 * Returns undefined if no references exist (avoids sending empty arrays).
 */
export const formItemsToReferences = (items: ArtifactReferenceFormItem[]): ArtifactReference[] | undefined => {
    if (items.length === 0) {
        return undefined;
    }
    const refs: ArtifactReference[] = items.map(item => ({
        groupId: item.groupId,
        artifactId: item.artifactId,
        version: item.version,
        name: item.name,
    }));
    return refs;
};
