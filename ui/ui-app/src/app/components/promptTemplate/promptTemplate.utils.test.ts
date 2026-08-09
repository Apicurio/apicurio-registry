import { describe, expect, it } from "vitest";
import { formatDefault, getVariablesList } from "./promptTemplate.utils";
import { PromptVariable } from "./promptTemplate.types";

describe("getVariablesList", () => {
    it("returns an empty array when variables is undefined", () => {
        expect(getVariablesList(undefined)).toEqual([]);
    });

    it("returns an empty array when variables is an empty array", () => {
        expect(getVariablesList([])).toEqual([]);
    });

    it("returns an empty array when variables is an empty object", () => {
        expect(getVariablesList({})).toEqual([]);
    });

    it("converts an array of variables using name property with empty string fallback", () => {
        const input: PromptVariable[] = [
            { name: "var1", type: "string" },
            { type: "number", description: "no name specified" }
        ];
        expect(getVariablesList(input)).toEqual([
            { name: "var1", variable: { name: "var1", type: "string" } },
            { name: "", variable: { type: "number", description: "no name specified" } }
        ]);
    });

    it("converts a record of variables using keys as names", () => {
        const input: Record<string, PromptVariable> = {
            model: { type: "string", default: "gpt-4o" },
            temperature: { type: "number", default: 0.7 }
        };
        expect(getVariablesList(input)).toEqual([
            { name: "model", variable: { type: "string", default: "gpt-4o" } },
            { name: "temperature", variable: { type: "number", default: 0.7 } }
        ]);
    });
});

describe("formatDefault", () => {
    it("formats string values", () => {
        expect(formatDefault("default-val")).toBe("default-val");
    });

    it("formats number values", () => {
        expect(formatDefault(42)).toBe("42");
        expect(formatDefault(3.14)).toBe("3.14");
    });

    it("formats boolean values", () => {
        expect(formatDefault(true)).toBe("true");
        expect(formatDefault(false)).toBe("false");
    });

    it("formats objects with JSON.stringify", () => {
        expect(formatDefault({ key: "val" })).toBe("{\"key\":\"val\"}");
    });

    it("formats arrays with JSON.stringify", () => {
        expect(formatDefault(["a", "b"])).toBe("[\"a\",\"b\"]");
    });

    it("formats null as string", () => {
        expect(formatDefault(null)).toBe("null");
    });

    it("formats undefined as string", () => {
        expect(formatDefault(undefined)).toBe("undefined");
    });
});
