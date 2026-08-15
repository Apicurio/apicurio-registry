import { describe, expect, it } from "vitest";
import { tokenizeTemplate } from "./PromptTemplateViewer.utils";

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

    it("treats unmatched opening delimiters as plain text", () => {
        expect(tokenizeTemplate("Hello {{")).toEqual([
            { text: "Hello {{", kind: "plain" }
        ]);
    });

    it("keeps a valid tag and treats a later unmatched opener as plain text", () => {
        expect(tokenizeTemplate("{{name}} leftover {{")).toEqual([
            { text: "{{name}}", kind: "variable" },
            { text: " leftover {{", kind: "plain" }
        ]);
    });

    it("treats an unmatched block comment opener as plain text", () => {
        expect(tokenizeTemplate("{{!-- never closed")).toEqual([
            { text: "{{!-- never closed", kind: "plain" }
        ]);
    });

    it("treats an unmatched triple-stash opener as plain text", () => {
        expect(tokenizeTemplate("{{{raw")).toEqual([
            { text: "{{{raw", kind: "plain" }
        ]);
    });

    it("does not rescan a large unmatched suffix", () => {
        const input = "{{".repeat(64_000);
        expect(tokenizeTemplate(input)).toEqual([
            { text: input, kind: "plain" }
        ]);
    }, 1000);
});
