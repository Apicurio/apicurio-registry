// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import {
    PROMPT_TEMPLATE_LANGUAGE_ID,
    registerPromptTemplate
} from "./PromptTemplateLanguage";

// JSDOM does not provide document.queryCommandSupported or window.matchMedia,
// which Monaco Editor 0.55.1 requires during standalone initialization.
if (typeof document !== "undefined" && !(document as any).queryCommandSupported) {
    (document as any).queryCommandSupported = () => false;
}
if (typeof window !== "undefined" && !window.matchMedia) {
    window.matchMedia = (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false
    } as any);
}

const monaco = await import("monaco-editor");

interface TokenResult {
    token: string;
    text: string;
}

/**
 * Tokenizes the input string using the real production Monaco Monarch tokenizer.
 * This runs the compiled Monarch engine in Monaco 0.55.1 without simulators or mock regexes.
 */
function tokenizeWithMonaco(input: string): TokenResult[] {
    registerPromptTemplate(monaco);
    const tokenLines = monaco.editor.tokenize(input, PROMPT_TEMPLATE_LANGUAGE_ID);
    const lines = input.split("\n");
    const results: TokenResult[] = [];

    for (let lineIdx = 0; lineIdx < tokenLines.length; lineIdx++) {
        const lineText = lines[lineIdx];
        const lineTokens = tokenLines[lineIdx];

        for (let idx = 0; idx < lineTokens.length; idx++) {
            const start = lineTokens[idx].offset;
            const end = idx < lineTokens.length - 1 ? lineTokens[idx + 1].offset : lineText.length;
            const rawType = lineTokens[idx].type;
            // Strip the language ID postfix appended by Monaco (e.g. "variable.prompt-template" -> "variable")
            const token = rawType ? rawType.replace(/\.prompt-template$/, "") : "";
            const text = lineText.slice(start, end);
            results.push({ token, text });
        }

        if (lineIdx < tokenLines.length - 1) {
            results.push({ token: "white", text: "\n" });
        }
    }

    return results;
}

describe("PromptTemplateLanguage — Real Monaco Tokenizer: Conditionals & Backend Grammar", () => {
    it("tokenizes valid conditionals matching PromptRenderingService contract as keywords", () => {
        const input = "{{#if active}} {{#unless isDraft}} {{else}} {{this}} {{/if}} {{/unless}}";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "keyword", text: "{{#if active}}" },
            { token: "keyword", text: "{{#unless isDraft}}" },
            { token: "keyword", text: "{{else}}" },
            { token: "keyword", text: "{{this}}" },
            { token: "keyword", text: "{{/if}}" },
            { token: "keyword", text: "{{/unless}}" }
        ]);
    });

    it("classifies unsupported helper expressions like {{#if (gt x 5)}} as invalid, NOT keyword", () => {
        const input = "{{#if (gt x 5)}}";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "invalid", text: "{{#if (gt x 5)}}" }
        ]);
    });

    it("classifies dotted conditional subjects like {{#unless user.name}} as invalid, NOT keyword", () => {
        const input = "{{#unless user.name}}";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "invalid", text: "{{#unless user.name}}" }
        ]);
    });

    it("classifies unsupported whitespace forms like {{ #if flag }} as invalid, NOT keyword", () => {
        const input = "{{ #if flag }}";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "invalid", text: "{{ #if flag }}" }
        ]);
    });

    it("tokenizes valid simple variables and rejects dotted paths as invalid", () => {
        const input = "{{name}} {{ name }} {{max_words}} {{user.name}}";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "variable", text: "{{name}}" },
            { token: "variable", text: "{{ name }}" },
            { token: "variable", text: "{{max_words}}" },
            { token: "invalid", text: "{{user.name}}" }
        ]);
    });

    it("tokenizes triple-brace expressions as invalid", () => {
        const input = "{{{rawContent}}}";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "invalid", text: "{{{rawContent}}}" }
        ]);
    });
});

describe("PromptTemplateLanguage — Real Monaco Tokenizer: YAML Block Scalar Contexts", () => {
    it("handles literal block scalar '|' with prompt #, apostrophes, template expressions and following variables", () => {
        const input = `templateId: demo
template: |
  # Hello {{name}}
  Don't forget {{name}}
variables:
  name:
    type: string`;

        const tokens = tokenizeWithMonaco(input);
        const significant = tokens.filter(t => t.token !== "white");

        // templateId: demo
        expect(significant[0]).toEqual({ token: "type.identifier", text: "templateId" });
        expect(significant[1]).toEqual({ token: "delimiter", text: ":" });
        expect(significant[2]).toEqual({ token: "string", text: "demo" });

        // template: |
        expect(significant[3]).toEqual({ token: "type.identifier", text: "template" });
        expect(significant[4]).toEqual({ token: "delimiter", text: ":" });
        expect(significant[5]).toEqual({ token: "operator", text: "|" });

        // Line with '# Hello {{name}}' inside block scalar:
        // Must NOT be treated as a comment; # is prompt text, {{name}} is variable.
        const hashLineToken = significant.find(t => t.text.includes("# Hello"));
        expect(hashLineToken).toBeDefined();
        expect(hashLineToken?.token).toBe("string");

        const firstVarToken = significant.find(t => t.text === "{{name}}");
        expect(firstVarToken).toBeDefined();
        expect(firstVarToken?.token).toBe("variable");

        // Line with "Don't forget {{name}}" inside block scalar:
        // Apostrophe in Don't must NOT open a string state that swallows following lines.
        const dontToken = significant.find(t => t.text.includes("Don't forget"));
        expect(dontToken).toBeDefined();
        expect(dontToken?.token).toBe("string");

        // variables: must be properly tokenized at document root level
        const variablesKey = significant.find(t => t.text === "variables");
        expect(variablesKey).toBeDefined();
        expect(variablesKey?.token).toBe("type.identifier");

        // name: and type: must also be recognized as YAML structure
        const nameKey = significant.find(t => t.text === "name");
        expect(nameKey).toBeDefined();
        expect(nameKey?.token).toBe("type.identifier");

        const typeKey = significant.find(t => t.text === "type");
        expect(typeKey).toBeDefined();
        expect(typeKey?.token).toBe("type.identifier");
    });

    it("handles folded block scalar '>' with chomping, comments on header line, and embedded quotes", () => {
        const input = `template: >- # folded scalar chomped
  Line with "double quotes" and 'single quotes'.
  Conditional {{#if showExtra}}with extra content{{/if}}.
variables:
  showExtra:
    type: boolean`;

        const tokens = tokenizeWithMonaco(input);
        const significant = tokens.filter(t => t.token !== "white");

        // template: >- # folded scalar chomped
        expect(significant[0]).toEqual({ token: "type.identifier", text: "template" });
        expect(significant[1]).toEqual({ token: "delimiter", text: ":" });
        expect(significant[2]).toEqual({ token: "operator", text: ">- # folded scalar chomped" });

        // Quotes inside block scalar are prompt text, not string delimiters
        const quotesLine = significant.find(t => t.text.includes("double quotes"));
        expect(quotesLine).toBeDefined();
        expect(quotesLine?.token).toBe("string");

        // Conditionals inside block scalar receive keyword highlighting
        const ifToken = significant.find(t => t.text === "{{#if showExtra}}");
        expect(ifToken).toBeDefined();
        expect(ifToken?.token).toBe("keyword");

        const closeIfToken = significant.find(t => t.text === "{{/if}}");
        expect(closeIfToken).toBeDefined();
        expect(closeIfToken?.token).toBe("keyword");

        // following variables: is restored to document level
        const variablesKey = significant.find(t => t.text === "variables");
        expect(variablesKey).toBeDefined();
        expect(variablesKey?.token).toBe("type.identifier");
    });

    it("handles nested block scalars indented inside maps and blank lines", () => {
        const input = `prompt:
  template: |+
    # First line with {{title}}

    Second line after blank: "quotes" and Don't.
  variables:
    title:
      type: string`;

        const tokens = tokenizeWithMonaco(input);
        const significant = tokens.filter(t => t.token !== "white");

        // prompt:
        expect(significant[0]).toEqual({ token: "type.identifier", text: "prompt" });
        expect(significant[1]).toEqual({ token: "delimiter", text: ":" });

        // template: |+
        expect(significant[2]).toEqual({ token: "type.identifier", text: "template" });
        expect(significant[3]).toEqual({ token: "delimiter", text: ":" });
        expect(significant[4]).toEqual({ token: "operator", text: "|+" });

        // # First line with {{title}}
        const hashLine = significant.find(t => t.text.includes("# First line with"));
        expect(hashLine).toBeDefined();
        expect(hashLine?.token).toBe("string");

        const titleVar = significant.find(t => t.text === "{{title}}");
        expect(titleVar).toBeDefined();
        expect(titleVar?.token).toBe("variable");

        // Post-blank line with quotes and apostrophe
        const secondLine = significant.find(t => t.text.includes("Second line after blank:"));
        expect(secondLine).toBeDefined();
        expect(secondLine?.token).toBe("string");

        // variables: at parent indent (2 spaces) ends the block scalar
        const variablesKey = significant.find(t => t.text === "variables");
        expect(variablesKey).toBeDefined();
        expect(variablesKey?.token).toBe("type.identifier");
    });
});

describe("PromptTemplateLanguage — Real Monaco Tokenizer: Quoted Strings", () => {
    it("tokenizes placeholders inside double-quoted strings separately from plain text", () => {
        const input = "template: \"Hello {{name}} at {{place}}\"";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "type.identifier", text: "template" },
            { token: "delimiter", text: ":" },
            { token: "string.quote", text: "\"" },
            { token: "string", text: "Hello " },
            { token: "variable", text: "{{name}}" },
            { token: "string", text: " at " },
            { token: "variable", text: "{{place}}" },
            { token: "string.quote", text: "\"" }
        ]);
    });

    it("tokenizes placeholders inside single-quoted strings separately from plain text", () => {
        const input = "template: 'Welcome {{user}}!'";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "type.identifier", text: "template" },
            { token: "delimiter", text: ":" },
            { token: "string.quote", text: "'" },
            { token: "string", text: "Welcome " },
            { token: "variable", text: "{{user}}" },
            { token: "string", text: "!" },
            { token: "string.quote", text: "'" }
        ]);
    });

    it("tokenizes conditionals (#if, else, /if) inside double-quoted strings", () => {
        const input = "template: \"Start.{{#if showExtra}} Extra.{{else}} None.{{/if}} End.\"";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "type.identifier", text: "template" },
            { token: "delimiter", text: ":" },
            { token: "string.quote", text: "\"" },
            { token: "string", text: "Start." },
            { token: "keyword", text: "{{#if showExtra}}" },
            { token: "string", text: " Extra." },
            { token: "keyword", text: "{{else}}" },
            { token: "string", text: " None." },
            { token: "keyword", text: "{{/if}}" },
            { token: "string", text: " End." },
            { token: "string.quote", text: "\"" }
        ]);
    });

    it("tokenizes escaped quotes correctly in quoted strings", () => {
        const input = "template: \"Say \\\"hello\\\"\"";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens).toEqual([
            { token: "type.identifier", text: "template" },
            { token: "delimiter", text: ":" },
            { token: "string.quote", text: "\"" },
            { token: "string", text: "Say " },
            { token: "string.escape", text: "\\\"" },
            { token: "string", text: "hello" },
            { token: "string.escape", text: "\\\"" },
            { token: "string.quote", text: "\"" }
        ]);
    });

    it("preserves standalone YAML comments with placeholders as comments", () => {
        const input = "# Example: {{name}} is required\ntemplate: \"Hello\"";
        const tokens = tokenizeWithMonaco(input).filter(t => t.token !== "white");

        expect(tokens[0]).toEqual({
            token: "comment",
            text: "# Example: {{name}} is required"
        });
    });
});

describe("PromptTemplateLanguage — Real Monaco Tokenizer: Incomplete Input Recovery", () => {
    it("recovers highlighting from incomplete template expression to valid variable", () => {
        // Incomplete expression mid-typing
        const incompleteInput = "template: \"Hello {{name";
        const incompleteTokens = tokenizeWithMonaco(incompleteInput).filter(t => t.token !== "white");
        // {{ is not closed so it is not tokenized as a variable
        expect(incompleteTokens.some(t => t.token === "variable")).toBe(false);

        // Completed expression
        const completeInput = "template: \"Hello {{name}}\"";
        const completeTokens = tokenizeWithMonaco(completeInput).filter(t => t.token !== "white");
        const varToken = completeTokens.find(t => t.text === "{{name}}");
        expect(varToken).toBeDefined();
        expect(varToken?.token).toBe("variable");
    });

    it("recovers highlighting from incomplete conditional to valid keyword", () => {
        // Incomplete conditional
        const incompleteInput = "template: \"{{#if active";
        const incompleteTokens = tokenizeWithMonaco(incompleteInput).filter(t => t.token !== "white");
        expect(incompleteTokens.some(t => t.token === "keyword")).toBe(false);

        // Completed conditional
        const completeInput = "template: \"{{#if active}}\"";
        const completeTokens = tokenizeWithMonaco(completeInput).filter(t => t.token !== "white");
        const kwToken = completeTokens.find(t => t.text === "{{#if active}}");
        expect(kwToken).toBeDefined();
        expect(kwToken?.token).toBe("keyword");
    });
});

describe("PromptTemplateLanguage — Registration Lifecycle", () => {
    function createMockMonaco() {
        const registeredLanguages: Array<{ id: string }> = [];
        let tokensProvider: any = null;

        return {
            languages: {
                getLanguages: () => [...registeredLanguages],
                register: (lang: { id: string }) => {
                    registeredLanguages.push(lang);
                },
                setMonarchTokensProvider: (id: string, provider: any) => {
                    tokensProvider = { id, provider };
                }
            },
            getRegisteredLanguages: () => registeredLanguages,
            getTokensProvider: () => tokensProvider
        };
    }

    it("registers language id 'prompt-template' and provides tokenizer config", () => {
        const mockMonaco = createMockMonaco();
        registerPromptTemplate(mockMonaco as any);

        expect(mockMonaco.getRegisteredLanguages()).toEqual([{ id: PROMPT_TEMPLATE_LANGUAGE_ID }]);
        const provider = mockMonaco.getTokensProvider();
        expect(provider).not.toBeNull();
        expect(provider.id).toBe(PROMPT_TEMPLATE_LANGUAGE_ID);
        expect(provider.provider.tokenizer).toBeDefined();
    });

    it("is instance-aware and idempotent on the same instance", () => {
        const mockMonaco = createMockMonaco();
        registerPromptTemplate(mockMonaco as any);
        registerPromptTemplate(mockMonaco as any);

        expect(mockMonaco.getRegisteredLanguages().length).toBe(1);
    });

    it("registers across separate independent Monaco instances", () => {
        const instance1 = createMockMonaco();
        const instance2 = createMockMonaco();

        registerPromptTemplate(instance1 as any);
        registerPromptTemplate(instance2 as any);

        expect(instance1.getRegisteredLanguages()).toEqual([{ id: PROMPT_TEMPLATE_LANGUAGE_ID }]);
        expect(instance2.getRegisteredLanguages()).toEqual([{ id: PROMPT_TEMPLATE_LANGUAGE_ID }]);
    });
});
