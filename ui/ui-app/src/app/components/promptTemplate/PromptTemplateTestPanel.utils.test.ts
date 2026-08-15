import { describe, expect, it } from "vitest";
import axios from "axios";
import {
    buildInitialValues,
    buildVersionIdentity,
    coerceEnumValue,
    hasAllRequiredValues,
    isAbortError,
    shouldAcceptRenderResponse
} from "./PromptTemplateTestPanel.utils";
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

describe("buildInitialValues", () => {
    it("uses declared defaults and boolean false when no default is set", () => {
        expect(buildInitialValues("Hello {{city}} {{enabled}}", {
            city: { type: "string", default: "Paris" },
            enabled: { type: "boolean" }
        })).toEqual({ city: "Paris", enabled: false });
    });

    it("includes template-detected names that are missing from the declared schema", () => {
        expect(buildInitialValues("Hello {{city}} {{name}}", {
            city: { type: "string", default: "Paris" }
        })).toEqual({ city: "Paris", name: "" });
    });

    it("supports array-shaped variable declarations", () => {
        expect(buildInitialValues(undefined, [
            { name: "city", type: "string", default: "Berlin" },
            { name: "enabled", type: "boolean", default: true }
        ])).toEqual({ city: "Berlin", enabled: true });
    });

    it("returns an empty map when there is no template and no variables", () => {
        expect(buildInitialValues(undefined, undefined)).toEqual({});
    });

    it("rebuilds defaults without leftover keys from a previous version's variables", () => {
        const version1 = buildInitialValues("Hello {{city}} {{oldVar}}", {
            city: { type: "string", default: "Paris" },
            oldVar: { type: "string", default: "stale" }
        });
        expect(version1).toEqual({ city: "Paris", oldVar: "stale" });

        const version2 = buildInitialValues("Hello {{city}}", {
            city: { type: "string", default: "Berlin" }
        });
        expect(version2).toEqual({ city: "Berlin" });
        expect(version2).not.toHaveProperty("oldVar");
    });

    it("preserves an explicit null default", () => {
        expect(buildInitialValues("Hello {{city}}", {
            city: { type: "string", default: null }
        })).toEqual({ city: null });
    });
});

describe("buildVersionIdentity", () => {
    it("changes when the version changes", () => {
        expect(buildVersionIdentity("default", "greeter", "1"))
            .not.toBe(buildVersionIdentity("default", "greeter", "2"));
    });

    it("changes when the artifact identity changes", () => {
        expect(buildVersionIdentity("default", "greeter", "1"))
            .not.toBe(buildVersionIdentity("other", "greeter", "1"));
    });
});

describe("shouldAcceptRenderResponse", () => {
    it("accepts the response for the latest request on the current version", () => {
        expect(shouldAcceptRenderResponse(3, 3, "g::a::1", "g::a::1")).toBe(true);
    });

    it("rejects a stale response after a newer Render starts", () => {
        expect(shouldAcceptRenderResponse(2, 3, "g::a::1", "g::a::1")).toBe(false);
    });

    it("rejects a stale response after the version identity changes", () => {
        expect(shouldAcceptRenderResponse(3, 3, "g::a::1", "g::a::2")).toBe(false);
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
