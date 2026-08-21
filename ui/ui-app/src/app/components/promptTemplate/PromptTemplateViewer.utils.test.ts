import { describe, expect, it } from "vitest";
import { extractPromptVariables, formatRange, tokenizeTemplate } from "./PromptTemplateViewer.utils";

describe("tokenizeTemplate", () => {
    it("keeps plain text without variables as a single plain token", () => {
        expect(tokenizeTemplate("Just some text")).toEqual([
            { text: "Just some text", kind: "plain" }
        ]);
    });

    it("highlights a simple variable surrounded by text", () => {
        expect(tokenizeTemplate("Hello {{name}}!")).toEqual([
            { text: "Hello ", kind: "plain" },
            { text: "{{name}}", kind: "variable" },
            { text: "!", kind: "plain" }
        ]);
    });

    it("handles an empty template", () => {
        expect(tokenizeTemplate("")).toEqual([]);
    });
});

describe("extractPromptVariables", () => {
    it("returns an empty array for empty content", () => {
        expect(extractPromptVariables("")).toEqual([]);
    });

    it("extracts double-brace variables", () => {
        expect(extractPromptVariables("Hello {{name}}, welcome to {{place}}!")).toEqual(["name", "place"]);
    });

    it("extracts single-brace variables", () => {
        expect(extractPromptVariables("Hello {name}, welcome to {place}!")).toEqual(["name", "place"]);
    });

    it("deduplicates repeated variables", () => {
        expect(extractPromptVariables("{{name}} and {name} again")).toEqual(["name"]);
    });

    it("excludes block helpers and comments", () => {
        expect(extractPromptVariables("{{#if user}}{{name}}{{/if}}{{!-- note --}}")).toEqual(["name"]);
    });

    it("returns no variables for plain text", () => {
        expect(extractPromptVariables("Just some text")).toEqual([]);
    });
});

describe("formatRange", () => {
    it("renders 'min – max' when both bounds are present", () => {
        expect(formatRange(1, 10)).toBe("1 – 10");
    });

    it("renders '≥ min' when only the minimum is present", () => {
        expect(formatRange(0, undefined)).toBe("≥ 0");
    });

    it("renders '≤ max' when only the maximum is present", () => {
        expect(formatRange(undefined, 100)).toBe("≤ 100");
    });

    it("returns null when neither bound is present", () => {
        expect(formatRange(undefined, undefined)).toBeNull();
    });
});