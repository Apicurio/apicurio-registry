import { describe, expect, it } from "vitest";
import { validatePropertyValue } from "./PropertyInput.utils";

describe("validatePropertyValue", () => {
    it("rejects an empty text value", () => {
        expect(validatePropertyValue("", "text")).toEqual({
            isValid: false,
            errorMessage: "Value cannot be empty"
        });
    });

    it("rejects a whitespace-only text value", () => {
        expect(validatePropertyValue("   ", "text")).toEqual({
            isValid: false,
            errorMessage: "Value cannot be empty"
        });
    });

    it("rejects an empty number value", () => {
        expect(validatePropertyValue("", "number")).toEqual({
            isValid: false,
            errorMessage: "Value cannot be empty"
        });
    });

    it("rejects a whitespace-only number value", () => {
        expect(validatePropertyValue("   ", "number")).toEqual({
            isValid: false,
            errorMessage: "Value cannot be empty"
        });
    });

    it("rejects a non-numeric number value", () => {
        expect(validatePropertyValue("abc", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("rejects a decimal number", () => {
        expect(validatePropertyValue("3.14", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("accepts a non-negative integer", () => {
        expect(validatePropertyValue("123", "number")).toEqual({
            isValid: true
        });
        expect(validatePropertyValue("0", "number")).toEqual({
            isValid: true
        });
    });

    it("rejects a negative integer", () => {
        expect(validatePropertyValue("-123", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("rejects scientific notation", () => {
        expect(validatePropertyValue("1e3", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("rejects hexadecimal notation", () => {
        expect(validatePropertyValue("0x10", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("rejects decimal notation with trailing zero", () => {
        expect(validatePropertyValue("1.0", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("rejects plus-prefixed integers", () => {
        expect(validatePropertyValue("+5", "number")).toEqual({
            isValid: false,
            errorMessage: "Value must be a non-negative integer"
        });
    });

    it("accepts non-empty text", () => {
        expect(validatePropertyValue("hello", "text")).toEqual({
            isValid: true
        });
    });
});