import { describe, expect, it } from "vitest";
import { ArtifactReferenceFormItem, formItemsToReferences, isReferencesValid, validateRefField } from "./ReferencesFormGroup.utils";

describe("ReferencesFormGroup validation helpers", () => {
    describe("validateRefField", () => {
        it("should return error for empty string", () => {
            expect(validateRefField("")).toBe("error");
        });

        it("should return error for invalid characters like slash or space", () => {
            expect(validateRefField("a/b")).toBe("error");
            expect(validateRefField("a b")).toBe("error");
            expect(validateRefField("a%b")).toBe("error");
        });

        it("should return success for valid identifier", () => {
            expect(validateRefField("my-valid-id_1.0")).toBe("success");
        });
    });

    describe("isReferencesValid", () => {
        it("should return true for empty list of reference items", () => {
            expect(isReferencesValid([])).toBe(true);
        });

        it("should return true when all reference items have valid values", () => {
            const validItems: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com.example",
                    artifactId: "my-artifact_1",
                    version: "1.0.0-SNAPSHOT",
                    name: "ref-name-1",
                },
                {
                    groupId: "group:2",
                    artifactId: "art+2",
                    version: "2.0.0,v1",
                    name: "ref.name.2",
                },
            ];
            expect(isReferencesValid(validItems)).toBe(true);
        });

        it("should return false if any field in a reference row is empty", () => {
            const itemsWithEmptyName: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com.example",
                    artifactId: "my-artifact",
                    version: "1.0.0",
                    name: "",
                },
            ];
            expect(isReferencesValid(itemsWithEmptyName)).toBe(false);

            const itemsWithEmptyGroupId: ArtifactReferenceFormItem[] = [
                {
                    groupId: "",
                    artifactId: "my-artifact",
                    version: "1.0.0",
                    name: "my-ref",
                },
            ];
            expect(isReferencesValid(itemsWithEmptyGroupId)).toBe(false);
        });

        it("should return false if a reference row contains slash (e.g. name: 'a/b')", () => {
            const itemsWithSlash: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com.example",
                    artifactId: "my-artifact",
                    version: "1.0.0",
                    name: "a/b",
                },
            ];
            expect(isReferencesValid(itemsWithSlash)).toBe(false);

            const itemsWithSlashInArtifactId: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com.example",
                    artifactId: "my/artifact",
                    version: "1.0.0",
                    name: "my-ref",
                },
            ];
            expect(isReferencesValid(itemsWithSlashInArtifactId)).toBe(false);
        });

        it("should return false if a reference row contains spaces", () => {
            const itemsWithSpace: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com example",
                    artifactId: "my-artifact",
                    version: "1.0.0",
                    name: "my-ref",
                },
            ];
            expect(isReferencesValid(itemsWithSpace)).toBe(false);
        });

        it("should return false if a reference row contains URL-unsafe or special characters (#, %, ?, @, etc.)", () => {
            const itemsWithHash: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com.example",
                    artifactId: "my#artifact",
                    version: "1.0.0",
                    name: "my-ref",
                },
            ];
            expect(isReferencesValid(itemsWithHash)).toBe(false);

            const itemsWithPercent: ArtifactReferenceFormItem[] = [
                {
                    groupId: "com.example",
                    artifactId: "my-artifact",
                    version: "1%0",
                    name: "my-ref",
                },
            ];
            expect(isReferencesValid(itemsWithPercent)).toBe(false);
        });
    });

    describe("formItemsToReferences", () => {
        it("should return undefined for empty items list", () => {
            expect(formItemsToReferences([])).toBeUndefined();
        });

        it("should correctly map form items to SDK ArtifactReference objects", () => {
            const items: ArtifactReferenceFormItem[] = [
                {
                    groupId: "group-1",
                    artifactId: "art-1",
                    version: "1.0",
                    name: "ref-1",
                },
            ];
            expect(formItemsToReferences(items)).toEqual([
                {
                    groupId: "group-1",
                    artifactId: "art-1",
                    version: "1.0",
                    name: "ref-1",
                },
            ]);
        });
    });
});
