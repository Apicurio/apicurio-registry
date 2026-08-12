import { describe, expect, it } from "vitest";
import { coerceEnumValue, describeNumericRange } from "./PromptTemplateTestPanel.utils";

describe("coerceEnumValue", () => {
    it("parses an integer enum selection to a number", () => {
        expect(coerceEnumValue("443", "integer")).toBe(443);
    });

    it("parses a number enum selection to a float", () => {
        expect(coerceEnumValue("3.14", "number")).toBe(3.14);
    });

    it("parses a boolean enum selection to true", () => {
        expect(coerceEnumValue("true", "boolean")).toBe(true);
    });

    it("parses a boolean enum selection to false", () => {
        expect(coerceEnumValue("false", "boolean")).toBe(false);
    });

    it("leaves a string enum selection untouched", () => {
        expect(coerceEnumValue("prod", "string")).toBe("prod");
    });

    it("keeps the placeholder selection as an empty string for an integer variable", () => {
        expect(coerceEnumValue("", "integer")).toBe("");
    });

    it("keeps the placeholder selection as an empty string for a number variable", () => {
        expect(coerceEnumValue("", "number")).toBe("");
    });

    it("keeps the placeholder selection as an empty string for a boolean variable", () => {
        expect(coerceEnumValue("", "boolean")).toBe("");
    });
});

describe("describeNumericRange", () => {
    it("describes both a minimum and a maximum", () => {
        expect(describeNumericRange(1, 10)).toBe("Must be between 1 and 10");
    });
    it("describes only a minimum", () => {
        expect(describeNumericRange(5, undefined)).toBe("Must be at least 5");
    });
    it("describes only a maximum", () => {
        expect(describeNumericRange(undefined, 100)).toBe("Must be at most 100");
    });
    it("returns undefined when neither bound is set", () => {
        expect(describeNumericRange(undefined, undefined)).toBeUndefined();
    });
});
