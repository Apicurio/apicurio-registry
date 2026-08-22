import { describe, expect, it } from "vitest";
import { extractPromptVariables, renderTemplatePreview, tokenizeTemplate } from "./PromptTemplateViewer.utils";

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

    it("extracts a mix of single- and double-brace variables", () => {
        expect(extractPromptVariables("{{greeting}}, {name}!")).toEqual(["greeting", "name"]);
    });

    it("deduplicates repeated variables", () => {
        expect(extractPromptVariables("{{name}} and {name} again")).toEqual(["name"]);
    });

    it("extracts dotted paths", () => {
        expect(extractPromptVariables("{{user.email}} / {user.id}")).toEqual(["user.email", "user.id"]);
    });

    it("excludes block helpers and comments", () => {
        expect(extractPromptVariables("{{#if user}}{{name}}{{/if}}{{!-- note --}}")).toEqual(["name"]);
    });

    it("ignores braces that aren't valid identifiers", () => {
        expect(extractPromptVariables('{"key": "value"} { } {123}')).toEqual([]);
    });

    it("returns no variables for plain text", () => {
        expect(extractPromptVariables("Just some text")).toEqual([]);
    });
});

describe("renderTemplatePreview", () => {
    it("returns an empty string for empty content", () => {
        expect(renderTemplatePreview("", { name: "Alice" })).toEqual("");
    });

    it("substitutes double-brace variables", () => {
        expect(renderTemplatePreview("Hello {{name}}!", { name: "Alice" })).toEqual("Hello Alice!");
    });

    it("substitutes single-brace variables", () => {
        expect(renderTemplatePreview("Hello {name}!", { name: "Alice" })).toEqual("Hello Alice!");
    });

    it("substitutes triple-stash variables", () => {
        expect(renderTemplatePreview("Raw: {{{name}}}", { name: "Alice" })).toEqual("Raw: Alice");
    });

    it("leaves placeholders with no value unsubstituted", () => {
        expect(renderTemplatePreview("Hello {{name}}!", {})).toEqual("Hello {{name}}!");
    });

    it("leaves placeholders with an empty-string value unsubstituted", () => {
        expect(renderTemplatePreview("Hello {{name}}!", { name: "" })).toEqual("Hello {{name}}!");
    });

    it("substitutes multiple different variables", () => {
        expect(renderTemplatePreview("{{greeting}}, {name}!", { greeting: "Hi", name: "Bob" })).toEqual("Hi, Bob!");
    });

    it("substitutes all occurrences of a repeated variable", () => {
        expect(renderTemplatePreview("{{name}} and {name} again", { name: "Alice" })).toEqual("Alice and Alice again");
    });
});
