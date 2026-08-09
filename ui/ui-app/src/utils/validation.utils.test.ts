import { describe, expect, it } from "vitest";
import { checkIdValid, isNonNegativeInteger, validateField } from "./validation.utils";

describe("validation.utils", () => {
    describe("checkIdValid", () => {
        it("should allow empty, null, or undefined values", () => {
            expect(checkIdValid("")).toBe(true);
            expect(checkIdValid(null)).toBe(true);
            expect(checkIdValid(undefined)).toBe(true);
        });

        it("should allow valid numeric, semantic, and artifact version strings", () => {
            expect(checkIdValid("1.2.3")).toBe(true);
            expect(checkIdValid("2.0.23")).toBe(true);
            expect(checkIdValid("10.2.1")).toBe(true);
            expect(checkIdValid("1.2.3-beta.1")).toBe(true);
            expect(checkIdValid("1.2.3+build.5")).toBe(true);
            expect(checkIdValid("2.0.0-SNAPSHOT")).toBe(true);
            expect(checkIdValid("v1.0.0")).toBe(true);
            expect(checkIdValid("my-group.artifact_1")).toBe(true);
            expect(checkIdValid("my:group:1.0")).toBe(true);
            expect(checkIdValid("group,id")).toBe(true);
        });

        it("should reject URL-unsafe and reserved characters (%, /, \\, #, ?, spaces, <, >, [, ], {, }, \", ', |)", () => {
            expect(checkIdValid("1%2")).toBe(false);
            expect(checkIdValid("1/2")).toBe(false);
            expect(checkIdValid("1\\2")).toBe(false);
            expect(checkIdValid("1#2")).toBe(false);
            expect(checkIdValid("1?2")).toBe(false);
            expect(checkIdValid("1 2")).toBe(false);
            expect(checkIdValid("1.0.0 ")).toBe(false);
            expect(checkIdValid("1<2")).toBe(false);
            expect(checkIdValid("1>2")).toBe(false);
            expect(checkIdValid("1[2]")).toBe(false);
            expect(checkIdValid("1{2}")).toBe(false);
            expect(checkIdValid("1\"2")).toBe(false);
            expect(checkIdValid("1'2")).toBe(false);
            expect(checkIdValid("1|2")).toBe(false);
        });

        it("should reject special characters (@, $, ^, *, &, (, ))", () => {
            expect(checkIdValid("1@2")).toBe(false);
            expect(checkIdValid("1$2")).toBe(false);
            expect(checkIdValid("1^2")).toBe(false);
            expect(checkIdValid("1*2")).toBe(false);
            expect(checkIdValid("1&2")).toBe(false);
            expect(checkIdValid("1(2)")).toBe(false);
        });

        it("should reject non-ASCII characters", () => {
            expect(checkIdValid("1.0.0🔥")).toBe(false);
            expect(checkIdValid("v1.0.0-🎉")).toBe(false);
            expect(checkIdValid("ver-ñ")).toBe(false);
        });
    });

    describe("validateField", () => {
        it("should return 'default' for empty or null input", () => {
            expect(validateField("")).toBe("default");
            expect(validateField(null)).toBe("default");
            expect(validateField(undefined)).toBe("default");
        });

        it("should return 'success' for valid input", () => {
            expect(validateField("1.2.3")).toBe("success");
            expect(validateField("my-artifact-id")).toBe("success");
        });

        it("should return 'error' for invalid input containing special/URL-unsafe characters", () => {
            expect(validateField("1#2")).toBe("error");
            expect(validateField("1/2")).toBe("error");
            expect(validateField("1%2")).toBe("error");
        });
    });

    describe("isNonNegativeInteger", () => {
        it("should return true for 0, 1, and 100", () => {
            expect(isNonNegativeInteger("0")).toBe(true);
            expect(isNonNegativeInteger("1")).toBe(true);
            expect(isNonNegativeInteger("100")).toBe(true);
        });

        it("should return false for negative numbers (-1, -58223)", () => {
            expect(isNonNegativeInteger("-1")).toBe(false);
            expect(isNonNegativeInteger("-58223")).toBe(false);
        });

        it("should return false for decimal numbers (10.5)", () => {
            expect(isNonNegativeInteger("10.5")).toBe(false);
            expect(isNonNegativeInteger("-0.5")).toBe(false);
        });

        it("should return false for empty, spaces, null, undefined, or non-numeric strings", () => {
            expect(isNonNegativeInteger("")).toBe(false);
            expect(isNonNegativeInteger("   ")).toBe(false);
            expect(isNonNegativeInteger(null)).toBe(false);
            expect(isNonNegativeInteger(undefined)).toBe(false);
            expect(isNonNegativeInteger("abc")).toBe(false);
        });
    });
});
