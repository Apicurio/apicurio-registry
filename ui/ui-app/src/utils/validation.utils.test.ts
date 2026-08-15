import { describe, expect, it } from "vitest";
import {
    checkIdValid,
    checkVersionValid,
    isReferencesValid,
    validateField,
    validateVersionField
} from "./validation.utils.ts";

describe("validation.utils", () => {
    describe("checkIdValid", () => {
        it("accepts null, undefined, empty string", () => {
            expect(checkIdValid(null)).toBe(true);
            expect(checkIdValid(undefined)).toBe(true);
            expect(checkIdValid("")).toBe(true);
        });

        it("accepts valid ASCII IDs without %", () => {
            expect(checkIdValid("my-artifact")).toBe(true);
            expect(checkIdValid("group.id.123")).toBe(true);
            expect(checkIdValid("my-artifact(v2)")).toBe(true);
            expect(checkIdValid("foo!~=;()*#&@")).toBe(true);
            expect(checkIdValid("with spaces")).toBe(true);
        });

        it("rejects % character", () => {
            expect(checkIdValid("artifact%1")).toBe(false);
            expect(checkIdValid("1%2")).toBe(false);
        });

        it("rejects non-ASCII characters", () => {
            expect(checkIdValid("artifact🔥")).toBe(false);
            expect(checkIdValid("group-ñ")).toBe(false);
        });

        it("rejects IDs over 512 characters", () => {
            const longId = "a".repeat(513);
            expect(checkIdValid(longId)).toBe(false);
            expect(checkIdValid("a".repeat(512))).toBe(true);
        });
    });

    describe("checkVersionValid", () => {
        it("accepts null, undefined, empty string", () => {
            expect(checkVersionValid(null)).toBe(true);
            expect(checkVersionValid(undefined)).toBe(true);
            expect(checkVersionValid("")).toBe(true);
        });

        it("accepts valid version strings matching [a-zA-Z0-9._\\-+]{1,256}", () => {
            expect(checkVersionValid("1.2.3")).toBe(true);
            expect(checkVersionValid("2.0.23")).toBe(true);
            expect(checkVersionValid("10.2.1")).toBe(true);
            expect(checkVersionValid("1.2.3-beta.1")).toBe(true);
            expect(checkVersionValid("1.2.3+build.5")).toBe(true);
            expect(checkVersionValid("2.0.0-SNAPSHOT")).toBe(true);
            expect(checkVersionValid("v1.0.0")).toBe(true);
        });

        it("rejects trailing or embedded newlines", () => {
            expect(checkVersionValid("1.0.0\n")).toBe(false);
            expect(checkVersionValid("1.0.0\r\n")).toBe(false);
            expect(checkVersionValid("\n1.0.0")).toBe(false);
        });

        it("rejects reserved or unsafe special characters, whitespace, non-ASCII", () => {
            expect(checkVersionValid("1.0:rc1")).toBe(false); // reserved :
            expect(checkVersionValid("1.0,rc1")).toBe(false); // reserved ,
            expect(checkVersionValid("1%2")).toBe(false);
            expect(checkVersionValid("1/2")).toBe(false);
            expect(checkVersionValid("1\\2")).toBe(false);
            expect(checkVersionValid("1#2")).toBe(false);
            expect(checkVersionValid("1?2")).toBe(false);
            expect(checkVersionValid("1@2")).toBe(false);
            expect(checkVersionValid("1$2")).toBe(false);
            expect(checkVersionValid("1^2")).toBe(false);
            expect(checkVersionValid("1*2")).toBe(false);
            expect(checkVersionValid("1(2)")).toBe(false);
            expect(checkVersionValid("1 2")).toBe(false);
            expect(checkVersionValid("1.0.0🔥")).toBe(false);
        });

        it("rejects version longer than 256 characters", () => {
            expect(checkVersionValid("1".repeat(257))).toBe(false);
            expect(checkVersionValid("1".repeat(256))).toBe(true);
        });
    });

    describe("validateField", () => {
        it("returns 'error' for invalid IDs", () => {
            expect(validateField("1%2")).toBe("error");
        });

        it("returns 'default' for empty values", () => {
            expect(validateField("")).toBe("default");
            expect(validateField(undefined)).toBe("default");
        });

        it("returns 'success' for valid IDs", () => {
            expect(validateField("valid-id")).toBe("success");
        });
    });

    describe("validateVersionField", () => {
        it("returns 'error' for invalid versions", () => {
            expect(validateVersionField("1:2")).toBe("error");
        });

        it("returns 'default' for empty values", () => {
            expect(validateVersionField("")).toBe("default");
            expect(validateVersionField(undefined)).toBe("default");
        });

        it("returns 'success' for valid versions", () => {
            expect(validateVersionField("1.0.0")).toBe("success");
        });
    });

    describe("isReferencesValid", () => {
        it("returns true for empty references list", () => {
            expect(isReferencesValid([])).toBe(true);
        });

        it("returns true for a fully valid reference row", () => {
            expect(isReferencesValid([
                { groupId: "default", artifactId: "my-artifact", version: "1.0.0", name: "ref1" }
            ])).toBe(true);
        });

        it("returns false if name contains %", () => {
            expect(isReferencesValid([
                { groupId: "default", artifactId: "my-artifact", version: "1.0.0", name: "ref%1" }
            ])).toBe(false);
        });

        it("returns false if groupId contains non-ASCII characters", () => {
            expect(isReferencesValid([
                { groupId: "group-ñ", artifactId: "my-artifact", version: "1.0.0", name: "ref1" }
            ])).toBe(false);
        });

        it("returns false if artifactId is 513 characters long", () => {
            expect(isReferencesValid([
                { groupId: "default", artifactId: "a".repeat(513), version: "1.0.0", name: "ref1" }
            ])).toBe(false);
        });

        it("returns false if version is invalid ('1 0' or '1.0\\n')", () => {
            expect(isReferencesValid([
                { groupId: "default", artifactId: "my-artifact", version: "1 0", name: "ref1" }
            ])).toBe(false);
            expect(isReferencesValid([
                { groupId: "default", artifactId: "my-artifact", version: "1.0.0\n", name: "ref1" }
            ])).toBe(false);
        });
    });
});

