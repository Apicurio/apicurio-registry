import { describe, expect, it } from "vitest";
import { formatRange, tokenizeTemplate } from "./PromptTemplateViewer.utils";

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

    it("classifies opening block helpers as blocks", () => {
        expect(tokenizeTemplate("{{#if user}}Hi{{/if}}")[0]).toEqual({
            text: "{{#if user}}",
            kind: "block"
        });
    });

    it("classifies closing block tags as blocks", () => {
        expect(tokenizeTemplate("{{/each}}")[0]).toEqual({
            text: "{{/each}}",
            kind: "block"
        });
    });

    it("classifies {{else}} as a block", () => {
        const tokens = tokenizeTemplate("{{#if a}}A{{else}}B{{/if}}");
        expect(tokens.map(t => [t.text, t.kind])).toEqual([
            ["{{#if a}}", "block"],
            ["A", "plain"],
            ["{{else}}", "block"],
            ["B", "plain"],
            ["{{/if}}", "block"]
        ]);
    });

    it("highlights dotted paths as a single variable", () => {
        expect(tokenizeTemplate("{{user.email}}")[0]).toEqual({
            text: "{{user.email}}",
            kind: "variable"
        });
    });

    it("highlights @index data variables", () => {
        expect(tokenizeTemplate("{{@index}}")[0]).toEqual({
            text: "{{@index}}",
            kind: "variable"
        });
    });

    it("highlights triple-stash variables as a single token", () => {
        expect(tokenizeTemplate("{{{raw}}}")).toEqual([
            { text: "{{{raw}}}", kind: "variable" }
        ]);
    });

    it("renders comments as plain text", () => {
        expect(tokenizeTemplate("{{!-- hidden --}}")).toEqual([
            { text: "{{!-- hidden --}}", kind: "plain" }
        ]);
    });

    it("renders single-bang comments as plain text", () => {
        expect(tokenizeTemplate("{{! hidden }}")).toEqual([
            { text: "{{! hidden }}", kind: "plain" }
        ]);
    });

    it("keeps tokens adjacent to single-bang comments intact", () => {
        expect(tokenizeTemplate("{{! c }}{{name}}")).toEqual([
            { text: "{{! c }}", kind: "plain" },
            { text: "{{name}}", kind: "variable" }
        ]);
    });

    it("classifies the inverse shorthand {{^}} as a block", () => {
        expect(tokenizeTemplate("{{#if a}}A{{^}}B{{/if}}").map(t => [t.text, t.kind])).toEqual([
            ["{{#if a}}", "block"],
            ["A", "plain"],
            ["{{^}}", "block"],
            ["B", "plain"],
            ["{{/if}}", "block"]
        ]);
    });

    it("keeps tokens adjacent to comments intact", () => {
        expect(tokenizeTemplate("{{!-- c --}}{{name}}")).toEqual([
            { text: "{{!-- c --}}", kind: "plain" },
            { text: "{{name}}", kind: "variable" }
        ]);
    });

    it("handles block helpers with as-expressions", () => {
        expect(tokenizeTemplate("{{#each items as |item|}}")[0]).toEqual({
            text: "{{#each items as |item|}}",
            kind: "block"
        });
    });

    it("handles an empty template", () => {
        expect(tokenizeTemplate("")).toEqual([]);
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

    it("ignores non-finite values", () => {
        expect(formatRange(NaN, Infinity)).toBeNull();
    });

    it("ignores null values", () => {
        // TS-loose call site (JSON parsers hand back null, not undefined)
        expect(formatRange(null as unknown as number, null as unknown as number)).toBeNull();
    });

    it("ignores stringified numbers", () => {
        // Number.isFinite does not coerce, so "5" is not treated as a bound
        expect(formatRange("5" as unknown as number, "10" as unknown as number)).toBeNull();
    });

    it("renders separated bounds when minimum > maximum (malformed schema)", () => {
        expect(formatRange(10, 1)).toBe("≥ 10, ≤ 1");
    });

    it("renders a normal range when minimum equals maximum", () => {
        expect(formatRange(5, 5)).toBe("5 – 5");
    });
});
