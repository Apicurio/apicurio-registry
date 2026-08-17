import { describe, expect, it } from "vitest";
import {
    buildInitialRawTexts,
    buildInitialValues,
    buildVersionIdentity,
    coerceEnumValue,
    initialObjectText,
    shouldAcceptRenderResponse
} from "./PromptTemplateTestPanel.utils";

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

describe("initialObjectText", () => {
    it("returns an empty string for undefined (no declared default)", () => {
        expect(initialObjectText(undefined)).toBe("");
    });

    it("returns the literal string 'null' for an explicit null default", () => {
        expect(initialObjectText(null)).toBe("null");
    });

    it("passes strings through unchanged (user-typed raw text)", () => {
        expect(initialObjectText("{\"partial\":")).toBe("{\"partial\":");
    });

    it("pretty-prints an object default", () => {
        expect(initialObjectText({ a: 1 })).toBe("{\n  \"a\": 1\n}");
    });

    it("pretty-prints an empty object", () => {
        expect(initialObjectText({})).toBe("{}");
    });

    it("pretty-prints an array default", () => {
        expect(initialObjectText([1, 2])).toBe("[\n  1,\n  2\n]");
    });
});

describe("buildInitialRawTexts", () => {
    it("returns an empty map when the template and variables are absent", () => {
        expect(buildInitialRawTexts(undefined, undefined)).toEqual({});
    });

    it("only tracks object and array fields, not primitives", () => {
        const variables = {
            cfg: { type: "object", default: { a: 1 } },
            note: { type: "string", default: "hi" },
            count: { type: "integer", default: 3 }
        };
        const result = buildInitialRawTexts("{{cfg}} {{note}} {{count}}", variables);
        expect(Object.keys(result).sort()).toEqual(["cfg"]);
        expect(result.cfg).toBe("{\n  \"a\": 1\n}");
    });

    it("preserves an explicit null default as the string 'null'", () => {
        const variables = { nul: { type: "object", default: null } };
        const result = buildInitialRawTexts("{{nul}}", variables);
        expect(result.nul).toBe("null");
    });

    it("returns an empty string when an object field has no default", () => {
        const variables = { cfg: { type: "object" } };
        const result = buildInitialRawTexts("{{cfg}}", variables);
        expect(result.cfg).toBe("");
    });
});
