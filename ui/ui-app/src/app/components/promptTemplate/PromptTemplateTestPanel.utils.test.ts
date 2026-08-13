import { describe, expect, it } from "vitest";
import { coerceEnumValue, describeNumericRange, findOutOfRangeErrors } from "./PromptTemplateTestPanel.utils";

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

describe("findOutOfRangeErrors", () => {
    it("returns no errors when values are within range", () => {
        const fields = [{ name: "rating", type: "integer", minimum: 1, maximum: 10 }];
        expect(findOutOfRangeErrors(fields, { rating: 5 })).toEqual([]);
    });
    it("flags a value below the minimum", () => {
        const fields = [{ name: "rating", type: "integer", minimum: 1, maximum: 10 }];
        const errors = findOutOfRangeErrors(fields, { rating: 0 });
        expect(errors).toEqual([{ variableName: "rating", message: "Value must be at least 1" }]);
    });
    it("flags a value above the maximum", () => {
        const fields = [{ name: "rating", type: "integer", minimum: 1, maximum: 10 }];
        const errors = findOutOfRangeErrors(fields, { rating: 999 });
        expect(errors).toEqual([{ variableName: "rating", message: "Value must be at most 10" }]);
    });
    it("ignores non-numeric field types", () => {
        const fields = [{ name: "label", type: "string", minimum: 1, maximum: 10 }];
        expect(findOutOfRangeErrors(fields, { label: "hello" })).toEqual([]);
    });
    it("ignores a field with no value set yet", () => {
        const fields = [{ name: "rating", type: "integer", minimum: 1, maximum: 10 }];
        expect(findOutOfRangeErrors(fields, { rating: "" })).toEqual([]);
    });
});
