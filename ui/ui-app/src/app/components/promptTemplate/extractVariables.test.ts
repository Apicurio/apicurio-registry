import { describe, expect, it } from "vitest";
import { extractVariables } from "./extractVariables";

describe("extractVariables", () => {
    it("extracts a single variable", () => {
        expect(extractVariables("Hello, {{name}}!")).toEqual(["name"]);
    });

    it("extracts multiple distinct variables", () => {
        expect(extractVariables("{{greeting}}, {{name}}! Welcome to {{place}}."))
            .toEqual(["greeting", "name", "place"]);
    });

    it("deduplicates repeated variables", () => {
        expect(extractVariables("{{name}} said hello to {{name}} and {{other}}"))
            .toEqual(["name", "other"]);
    });

    it("trims whitespace inside braces", () => {
        expect(extractVariables("{{ name }} and {{  place  }}"))
            .toEqual(["name", "place"]);
    });

    it("returns empty array for template with no variables", () => {
        expect(extractVariables("This is plain text with no variables."))
            .toEqual([]);
    });

    it("returns empty array for empty string", () => {
        expect(extractVariables("")).toEqual([]);
    });

    it("returns empty array for null/undefined input", () => {
        expect(extractVariables(null as unknown as string)).toEqual([]);
        expect(extractVariables(undefined as unknown as string)).toEqual([]);
    });

    it("skips malformed empty braces {{ }}", () => {
        expect(extractVariables("before {{ }} after {{valid}}"))
            .toEqual(["valid"]);
    });

    it("skips Handlebars block helpers like {{#if}} and {{/if}}", () => {
        expect(extractVariables("{{#if show}}Hello {{name}}{{/if}}"))
            .toEqual(["name"]);
    });

    it("skips {{#each}}, {{#unless}}, {{#with}} block helpers", () => {
        const template = "{{#each items}}{{value}}{{/each}} {{#unless hidden}}{{label}}{{/unless}}";
        expect(extractVariables(template)).toEqual(["value", "label"]);
    });

    it("handles variables adjacent to text without spaces", () => {
        expect(extractVariables("prefix{{a}}middle{{b}}suffix"))
            .toEqual(["a", "b"]);
    });

    it("preserves order of first appearance", () => {
        expect(extractVariables("{{z}} {{a}} {{m}} {{z}}"))
            .toEqual(["z", "a", "m"]);
    });

    // --- Tests for keyword exclusion (else, this, lookup, log) ---

    it("excludes {{else}} from an if/else block", () => {
        // Note: 'formal' is a block argument inside {{#if formal}}, not a
        // standalone variable — the regex captures '#if formal' as one token
        // and the ^[#/] prefix check skips it. This matches existing behavior
        // (see the {{#if show}} test above which also doesn't extract 'show').
        expect(extractVariables("{{#if formal}}Mr{{else}}Ms{{/if}}"))
            .toEqual([]);
    });

    it("excludes {{this}} from an each block", () => {
        // 'items' is a block argument inside {{#each items}}, same as above.
        expect(extractVariables("{{#each items}}{{this}}{{/each}}"))
            .toEqual([]);
    });

    it("excludes {{lookup}} and {{log}} helpers", () => {
        expect(extractVariables("{{lookup obj key}} {{log message}}"))
            .toEqual([]);
    });

    it("excludes {{else}} but keeps standalone variables (regression)", () => {
        // {{#if x}} skips 'x' as a block argument, but the standalone {{x}}
        // at the end IS a real variable and should be extracted.
        expect(extractVariables("{{#if x}}{{else}}{{/if}} {{x}}"))
            .toEqual(["x"]);
    });
});
