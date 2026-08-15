import { describe, expect, it } from "vitest";
import axios from "axios";
import { coerceEnumValue, hasAllRequiredValues, isAbortError } from "./PromptTemplateTestPanel.utils";
import { ReconciledVariable } from "./promptTemplateVariables";

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

const requiredVar = (name: string): ReconciledVariable => ({
    name,
    schema: { type: "string", required: true },
    source: "both"
});

const optionalVar = (name: string): ReconciledVariable => ({
    name,
    schema: { type: "string", required: false },
    source: "both"
});

describe("hasAllRequiredValues", () => {
    it("returns false when a required field is empty", () => {
        expect(hasAllRequiredValues([requiredVar("name")], { name: "" })).toBe(false);
    });

    it("returns true when a required field is filled", () => {
        expect(hasAllRequiredValues([requiredVar("name")], { name: "Ada" })).toBe(true);
    });

    it("returns true when an optional field is empty", () => {
        expect(hasAllRequiredValues([optionalVar("title")], { title: "" })).toBe(true);
    });

    it("returns true when a required field is filled and an optional field is empty", () => {
        expect(hasAllRequiredValues(
            [requiredVar("name"), optionalVar("title")],
            { name: "Ada", title: "" }
        )).toBe(true);
    });

    it("returns false when a required field is empty even if an optional field is filled", () => {
        expect(hasAllRequiredValues(
            [requiredVar("name"), optionalVar("title")],
            { name: "", title: "Dr" }
        )).toBe(false);
    });

    it("treats boolean false as filled", () => {
        const flag: ReconciledVariable = {
            name: "enabled",
            schema: { type: "boolean", required: true },
            source: "both"
        };
        expect(hasAllRequiredValues([flag], { enabled: false })).toBe(true);
    });

    it("treats number 0 as filled", () => {
        const count: ReconciledVariable = {
            name: "count",
            schema: { type: "integer", required: true },
            source: "both"
        };
        expect(hasAllRequiredValues([count], { count: 0 })).toBe(true);
    });

    it("treats null and undefined as empty for a required field", () => {
        expect(hasAllRequiredValues([requiredVar("name")], { name: null })).toBe(false);
        expect(hasAllRequiredValues([requiredVar("name")], {})).toBe(false);
    });
});

describe("isAbortError", () => {
    it("detects a real AbortError-shaped object", () => {
        const err = new Error("The operation was aborted");
        err.name = "AbortError";
        expect(isAbortError(err)).toBe(true);
    });

    it("detects a CanceledError from axios", () => {
        expect(isAbortError(new axios.CanceledError("canceled"))).toBe(true);
    });

    it("returns false for a normal Error", () => {
        expect(isAbortError(new Error("Error rendering prompt template"))).toBe(false);
    });

    it("returns false for an axios error with a response", () => {
        const err = {
            name: "AxiosError",
            message: "Request failed with status code 400",
            response: { status: 400, data: { message: "bad request" } }
        };
        expect(isAbortError(err)).toBe(false);
    });
});
