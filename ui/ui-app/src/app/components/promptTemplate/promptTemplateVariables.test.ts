import { describe, expect, it } from "vitest";
import {
    extractTemplateVariableNames,
    reconcileTemplateVariables,
    VariableSchema
} from "./promptTemplateVariables";

describe("extractTemplateVariableNames", () => {
    it("returns an empty array when the template has no variables", () => {
        expect(extractTemplateVariableNames("Hello world")).toEqual([]);
        expect(extractTemplateVariableNames("")).toEqual([]);
    });

    it("extracts a single variable", () => {
        expect(extractTemplateVariableNames("Hello {{name}}!")).toEqual(["name"]);
    });

    it("deduplicates repeated occurrences in first-seen order", () => {
        expect(extractTemplateVariableNames("{{a}} then {{b}} then {{a}}")).toEqual(["a", "b"]);
    });

    it("extracts block variables and excludes block keywords", () => {
        expect(extractTemplateVariableNames("{{#if x}}yes{{/if}}")).toEqual(["x"]);
        expect(extractTemplateVariableNames("{{#each y}}item{{/each}}")).toEqual(["y"]);
        expect(extractTemplateVariableNames("{{#unless formal}}hi{{/unless}}")).toEqual(["formal"]);
        expect(extractTemplateVariableNames("{{#with ctx}}body{{/with}}")).toEqual(["ctx"]);
    });

    it("handles a mix of plain and block variables", () => {
        const template = "Hello {{name}}{{#if formal}}, sir{{/if}}. Items: {{#each items}}{{item}}{{/each}}";
        expect(extractTemplateVariableNames(template)).toEqual(["name", "formal", "items", "item"]);
    });
});

describe("reconcileTemplateVariables", () => {
    it("marks template-only variables as detected", () => {
        const result = reconcileTemplateVariables(["name"], undefined);
        expect(result).toEqual([
            { name: "name", schema: undefined, source: "detected" }
        ]);
    });

    it("marks schema-only variables as declared", () => {
        const declared: Record<string, VariableSchema> = {
            unused: { type: "string", required: true }
        };
        const result = reconcileTemplateVariables([], declared);
        expect(result).toEqual([
            { name: "unused", schema: declared.unused, source: "declared" }
        ]);
    });

    it("marks variables present in both as both", () => {
        const declared: Record<string, VariableSchema> = {
            name: { type: "string", required: true, description: "Name" }
        };
        const result = reconcileTemplateVariables(["name"], declared);
        expect(result).toEqual([
            { name: "name", schema: declared.name, source: "both" }
        ]);
    });

    it("handles empty or undefined declared variables map", () => {
        expect(reconcileTemplateVariables(["a", "b"], undefined)).toEqual([
            { name: "a", schema: undefined, source: "detected" },
            { name: "b", schema: undefined, source: "detected" }
        ]);
        expect(reconcileTemplateVariables(["a"], {})).toEqual([
            { name: "a", schema: undefined, source: "detected" }
        ]);
    });

    it("preserves template order then appends unused declared names", () => {
        const declared: Record<string, VariableSchema> = {
            unused: { type: "boolean" },
            name: { type: "string" }
        };
        const result = reconcileTemplateVariables(["name", "extra"], declared);
        expect(result.map(r => ({ name: r.name, source: r.source }))).toEqual([
            { name: "name", source: "both" },
            { name: "extra", source: "detected" },
            { name: "unused", source: "declared" }
        ]);
    });
});
