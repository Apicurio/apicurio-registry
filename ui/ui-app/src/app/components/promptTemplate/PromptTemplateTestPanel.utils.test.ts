import { describe, expect, it } from "vitest";
import { coerceEnumValue } from "./PromptTemplateTestPanel.utils";
import { RenderPromptValidationError } from "@models/RenderPromptResponse";

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

describe("RenderPromptValidationError", () => {
    it("has variableName field for displaying which variable failed", () => {
        const error: RenderPromptValidationError = {
            variableName: "temperature",
            message: "Type mismatch: expected number but got string",
            expectedType: "number",
            actualType: "string",
        };

        expect(error.variableName).toBe("temperature");
        expect(error.message).toBe("Type mismatch: expected number but got string");
        expect(error.expectedType).toBe("number");
        expect(error.actualType).toBe("string");
    });

    it("variableName is optional - template-level errors may not have a variable", () => {
        const error: RenderPromptValidationError = {
            message: "Template rendering failed",
        };

        expect(error.variableName).toBeUndefined();
        expect(error.message).toBe("Template rendering failed");
    });

    it("variableName and message are required fields when variableName is present", () => {
        const error: RenderPromptValidationError = {
            variableName: "enabled",
            message: "Variable is required",
        };

        expect(error.variableName).toBeTruthy();
        expect(error.message).toBeTruthy();
    });
});

describe("number input parsing", () => {
    const parseNumber = (val: string, type: "integer" | "number"): string | number => {
        const n = type === "integer" ? parseInt(val) : parseFloat(val);
        return Number.isNaN(n) ? "" : n;
    };

    it("parses valid integer string", () => {
        expect(parseNumber("42", "integer")).toBe(42);
    });

    it("parses valid float string", () => {
        expect(parseNumber("3.14", "number")).toBe(3.14);
    });

    it("returns empty string for empty input (integer)", () => {
        expect(parseNumber("", "integer")).toBe("");
    });

    it("returns empty string for empty input (number)", () => {
        expect(parseNumber("", "number")).toBe("");
    });

    it("returns empty string for non-numeric input (integer)", () => {
        expect(parseNumber("abc", "integer")).toBe("");
    });

    it("returns empty string for non-numeric input (number)", () => {
        expect(parseNumber("abc", "number")).toBe("");
    });

    it("handles zero correctly (integer)", () => {
        expect(parseNumber("0", "integer")).toBe(0);
    });

    it("handles zero correctly (number)", () => {
        expect(parseNumber("0", "number")).toBe(0);
    });

    it("handles negative numbers (integer)", () => {
        expect(parseNumber("-5", "integer")).toBe(-5);
    });

    it("handles negative numbers (number)", () => {
        expect(parseNumber("-2.5", "number")).toBe(-2.5);
    });
});