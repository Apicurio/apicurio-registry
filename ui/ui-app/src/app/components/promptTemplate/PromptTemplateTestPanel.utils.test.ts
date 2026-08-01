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

    it("variableName and message are required fields", () => {
        const error: RenderPromptValidationError = {
            variableName: "enabled",
            message: "Variable is required",
        };

        expect(error.variableName).toBeTruthy();
        expect(error.message).toBeTruthy();
    });
});