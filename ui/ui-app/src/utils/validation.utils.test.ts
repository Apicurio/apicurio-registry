import { describe, expect, it } from "vitest";
import { checkIdValid, validateField } from "./validation.utils";

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
});
