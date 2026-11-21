import { describe, expect, it } from "vitest";
import {
    extractTemplateVariableNames,
    extractTemplateVariableReferences,
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

    it("extracts the subjects of {{#if}} and {{#unless}}, the blocks the backend renders", () => {
        expect(extractTemplateVariableNames("{{#if x}}yes{{/if}}")).toEqual(["x"]);
        expect(extractTemplateVariableNames("{{#unless formal}}hi{{/unless}}")).toEqual(["formal"]);
        expect(extractTemplateVariableNames("{{#if  x}}yes{{/if}}")).toEqual(["x"]);
    });

    it("names no variable in unsupported {{#each}} and {{#with}} blocks", () => {
        expect(extractTemplateVariableNames("{{#each y}}item{{/each}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{#with ctx}}body{{/with}}")).toEqual([]);
    });

    it("handles a mix of plain and block variables", () => {
        const template = "Hello {{name}}{{#if formal}}, sir{{/if}}. Items: {{#each items}}{{item}}{{/each}}";
        expect(extractTemplateVariableNames(template)).toEqual(["name", "formal", "item"]);
    });

    it("tolerates whitespace inside braces for plain variables", () => {
        expect(extractTemplateVariableNames("Hello {{ name }}!")).toEqual(["name"]);
        expect(extractTemplateVariableNames("Hello {{  name  }}!")).toEqual(["name"]);
        expect(extractTemplateVariableNames("{{name }} and {{ name}}")).toEqual(["name"]);
    });

    it("does not recognize spaced conditional tags as blocks", () => {
        expect(extractTemplateVariableNames("{{ #if x }}yes{{/if}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{ #if x}}yes{{/if}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{#if x }}yes{{/if}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{ #unless x }}yes{{/unless}}")).toEqual([]);
    });

    it("treats bare {{lookup}}, {{log}} and {{if}} as variables, as the backend does", () => {
        expect(extractTemplateVariableNames("{{lookup}} {{log}} {{if}}")).toEqual(["lookup", "log", "if"]);
        expect(extractTemplateVariableNames("{{ lookup }}")).toEqual(["lookup"]);
    });

    it("does not mistake helper expressions for bare names", () => {
        expect(extractTemplateVariableNames("{{lookup obj key}} {{log \"hi\"}} {{if flag}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{#if (gt x 5)}}big{{/if}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{lookup items 0}} then {{lookup}}")).toEqual(["lookup"]);
    });

    it("deduplicates the same name across spaced and unspaced spellings", () => {
        expect(extractTemplateVariableNames("{{name}} and {{ name }}")).toEqual(["name"]);
    });

    it("excludes else and this when they appear as bare tag names", () => {
        expect(extractTemplateVariableNames("{{#if x}}a{{else}}b{{/if}}")).toEqual(["x"]);
        expect(extractTemplateVariableNames("{{this}} {{ else }} {{ this }}")).toEqual([]);
    });

    it("extracts variables from nested blocks", () => {
        expect(extractTemplateVariableNames("{{#if a}}{{#unless b}}{{item}}{{/unless}}{{/if}}"))
            .toEqual(["a", "b", "item"]);
    });

    it("extracts the name from triple-brace raw-output syntax", () => {
        expect(extractTemplateVariableNames("{{{name}}}")).toEqual(["name"]);
    });

    it("ignores Handlebars comment syntax", () => {
        expect(extractTemplateVariableNames("{{! a comment }}{{name}}")).toEqual(["name"]);
        expect(extractTemplateVariableNames("{{!-- comment --}}{{name}}")).toEqual(["name"]);
    });

    it("treats prototype-collision-prone names as ordinary variable strings", () => {
        // Documents that extraction uses Set/string equality — no JS object
        // prototype behavior leaks into the result.
        expect(extractTemplateVariableNames("{{constructor}} {{toString}} {{__proto__}} {{hasOwnProperty}}"))
            .toEqual(["constructor", "toString", "__proto__", "hasOwnProperty"]);
    });

    it("does not capture dotted paths (matches backend \\w+ scope)", () => {
        // Backend PromptTemplateVariableUtil also uses \\w+ only; {{user.name}}
        // is intentionally out of scope for both frontend and backend.
        expect(extractTemplateVariableNames("{{user.name}}")).toEqual([]);
    });

    it("does not capture @index-style tokens (matches backend scope)", () => {
        // @index/@key/@first start with @ and fall outside \\w+. Backend does
        // not support them either; if that changes, update this frontend too.
        expect(extractTemplateVariableNames("{{@index}} {{@key}}")).toEqual([]);
    });
});

describe("extractTemplateVariableReferences", () => {
    it("keeps condition subjects separate from substitution placeholders", () => {
        expect(extractTemplateVariableReferences("{{#if urgent}}Escalate.{{/if}} Hi {{name}}")).toEqual([
            { name: "urgent", placeholder: false, condition: true },
            { name: "name", placeholder: true, condition: false }
        ]);
        expect(extractTemplateVariableReferences("{{#unless premium}}Upgrade{{/unless}}")).toEqual([
            { name: "premium", placeholder: false, condition: true }
        ]);
    });

    it("marks a name used both as a condition and as a placeholder once, with both flags", () => {
        expect(extractTemplateVariableReferences("{{#if urgent}}Urgent: {{urgent}}{{/if}} {{ urgent }}")).toEqual([
            { name: "urgent", placeholder: true, condition: true }
        ]);
    });

    it("does not report the subject of a spaced conditional tag as a condition", () => {
        expect(extractTemplateVariableReferences("{{ #if urgent }}x{{/if}}")).toEqual([]);
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
