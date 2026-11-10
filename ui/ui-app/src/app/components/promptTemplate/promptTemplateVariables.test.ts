import { describe, expect, it } from "vitest";
import {
    classifyHandlebarsTag,
    extractTemplateVariableNames,
    parseHandlebarsTagInner,
    reconcileTemplateVariables,
    VariableSchema
} from "./promptTemplateVariables";

describe("parseHandlebarsTagInner", () => {
    it("keeps whitespace-control tildes on block tags", () => {
        expect(parseHandlebarsTagInner("{{~#each items~}}")).toEqual({
            inner: "~#each items~",
            head: "~#each"
        });
    });

    it("keeps whitespace-control tildes on plain variables", () => {
        expect(parseHandlebarsTagInner("{{~ name ~}}")).toEqual({
            inner: "~ name ~",
            head: "~"
        });
    });
});

describe("classifyHandlebarsTag", () => {
    it("classifies plain and triple-stash variables as variables", () => {
        expect(classifyHandlebarsTag("{{name}}")).toBe("variable");
        expect(classifyHandlebarsTag("{{ name }}")).toBe("variable");
        expect(classifyHandlebarsTag("{{{raw}}}")).toBe("variable");
    });

    it("classifies block helpers as blocks", () => {
        expect(classifyHandlebarsTag("{{#if user}}")).toBe("block");
        expect(classifyHandlebarsTag("{{/if}}")).toBe("block");
        expect(classifyHandlebarsTag("{{else}}")).toBe("block");
    });

    it("classifies dotted paths and @-prefixed tokens as plain", () => {
        expect(classifyHandlebarsTag("{{user.email}}")).toBe("plain");
        expect(classifyHandlebarsTag("{{@index}}")).toBe("plain");
        expect(classifyHandlebarsTag("{{{user.email}}}")).toBe("plain");
    });

    it("classifies handlebars keywords as plain", () => {
        expect(classifyHandlebarsTag("{{this}}")).toBe("plain");
    });

    it("classifies whitespace-control block tags as plain (not processed)", () => {
        expect(classifyHandlebarsTag("{{~#each items~}}")).toBe("plain");
        expect(classifyHandlebarsTag("{{~/if~}}")).toBe("plain");
    });

    it("classifies whitespace-control variables as plain (not substituted)", () => {
        expect(classifyHandlebarsTag("{{~ item ~}}")).toBe("plain");
        expect(classifyHandlebarsTag("{{~item}}")).toBe("plain");
        expect(classifyHandlebarsTag("{{item~}}")).toBe("plain");
    });
});

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

    it("tolerates whitespace inside braces for plain variables", () => {
        expect(extractTemplateVariableNames("Hello {{ name }}!")).toEqual(["name"]);
        expect(extractTemplateVariableNames("Hello {{  name  }}!")).toEqual(["name"]);
        expect(extractTemplateVariableNames("{{name }} and {{ name}}")).toEqual(["name"]);
    });

    it("tolerates whitespace inside braces for block variables", () => {
        expect(extractTemplateVariableNames("{{ #if x }}yes{{/if}}")).toEqual(["x"]);
        expect(extractTemplateVariableNames("{{#if  x  }}yes{{/if}}")).toEqual(["x"]);
    });

    it("deduplicates the same name across spaced and unspaced spellings", () => {
        expect(extractTemplateVariableNames("{{name}} and {{ name }}")).toEqual(["name"]);
    });

    it("excludes else and this when they appear as bare tag names", () => {
        expect(extractTemplateVariableNames("{{#if x}}a{{else}}b{{/if}}")).toEqual(["x"]);
        expect(extractTemplateVariableNames("{{#each items}}{{this}}{{/each}}")).toEqual(["items"]);
    });

    it("extracts variables from nested blocks", () => {
        expect(extractTemplateVariableNames("{{#if a}}{{#each b}}{{item}}{{/each}}{{/if}}"))
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

    it("does not capture whitespace-control variables (matches backend scope)", () => {
        expect(extractTemplateVariableNames("{{~ name ~}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{~name}}")).toEqual([]);
        expect(extractTemplateVariableNames("{{name~}}")).toEqual([]);
    });

    it("does not capture @index-style tokens (matches backend scope)", () => {
        // @index/@key/@first start with @ and fall outside \\w+. Backend does
        // not support them either; if that changes, update this frontend too.
        expect(extractTemplateVariableNames("{{#each items}}{{@index}}{{/each}}")).toEqual(["items"]);
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
