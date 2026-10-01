import { describe, expect, it } from "vitest";
import {
    PROMPT_TEMPLATE_LANGUAGE_ID,
    promptTemplateMonarchTokensProvider,
    registerPromptTemplate
} from "./PromptTemplateLanguage";

interface TokenResult {
    token: string;
    text: string;
}

/**
 * Lightweight Monarch line-tokenizer simulator for testing Monarch rules in Node.
 */
function tokenizeMonarch(input: string): TokenResult[] {
    const tokenizer = promptTemplateMonarchTokensProvider.tokenizer as Record<string, any[]>;
    const stateStack: string[] = ["root"];
    const results: TokenResult[] = [];

    function getRulesForState(stateName: string): any[] {
        const rawRules = tokenizer[stateName] || [];
        const expandedRules: any[] = [];
        for (const rule of rawRules) {
            if (rule.include && typeof rule.include === "string" && rule.include.startsWith("@")) {
                expandedRules.push(...getRulesForState(rule.include.slice(1)));
            } else {
                expandedRules.push(rule);
            }
        }
        return expandedRules;
    }

    const lines = input.split("\n");

    for (let l = 0; l < lines.length; l++) {
        const line = lines[l];
        let pos = 0;

        while (pos < line.length) {
            const currentState = stateStack[stateStack.length - 1];
            const rules = getRulesForState(currentState);
            const remaining = line.slice(pos);
            let matched = false;

            for (const rule of rules) {
                const pattern: RegExp = rule[0];
                const action: any = rule[1];

                const regex = new RegExp(pattern.source, pattern.flags.replace("g", ""));
                const match = regex.exec(remaining);

                if (match && match.index === 0 && match[0].length > 0) {
                    const matchText = match[0];
                    let tokenType = "";

                    if (typeof action === "string") {
                        tokenType = action;
                    } else if (typeof action === "object" && action !== null) {
                        tokenType = action.token || "";
                        if (action.next === "@pop") {
                            if (stateStack.length > 1) {
                                stateStack.pop();
                            }
                        } else if (action.next && typeof action.next === "string" && action.next.startsWith("@")) {
                            stateStack.push(action.next.slice(1));
                        }
                    }

                    results.push({ token: tokenType, text: matchText });
                    pos += matchText.length;
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                results.push({ token: "default", text: line[pos] });
                pos += 1;
            }
        }

        if (l < lines.length - 1) {
            results.push({ token: "white", text: "\n" });
        }
    }

    return results;
}

describe("PromptTemplateLanguage — Monarch Quoted String Tokenizer Regression Tests", () => {
    it("tokenizes placeholders inside double-quoted strings separately from plain text", () => {
        const input = "template: \"Hello {{name}} at {{place}}\"";
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant).toEqual([
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
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant).toEqual([
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
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant).toEqual([
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

    it("tokenizes #unless inside quoted strings", () => {
        const input = "template: \"{{#unless premium}}Upgrade now.{{/unless}}\"";
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant).toEqual([
            { token: "type.identifier", text: "template" },
            { token: "delimiter", text: ":" },
            { token: "string.quote", text: "\"" },
            { token: "keyword", text: "{{#unless premium}}" },
            { token: "string", text: "Upgrade now." },
            { token: "keyword", text: "{{/unless}}" },
            { token: "string.quote", text: "\"" }
        ]);
    });

    it("tokenizes escaped quotes and normal strings without placeholders correctly", () => {
        const input = "template: \"Say \\\"hello\\\"\"";
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant).toEqual([
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

    it("tokenizes triple braces inside quotes as invalid", () => {
        const input = "template: \"Raw: {{{rawContent}}}\"";
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant).toEqual([
            { token: "type.identifier", text: "template" },
            { token: "delimiter", text: ":" },
            { token: "string.quote", text: "\"" },
            { token: "string", text: "Raw: " },
            { token: "invalid", text: "{{{rawContent}}}" },
            { token: "string.quote", text: "\"" }
        ]);
    });

    it("preserves comments with placeholders as comments (comment priority)", () => {
        const input = "# Example: {{name}} is required\ntemplate: \"Hello\"";
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        expect(significant[0]).toEqual({
            token: "comment",
            text: "# Example: {{name}} is required"
        });
    });

    it("does not tokenize dotted paths inside quotes as variables", () => {
        const input = "template: \"Hello {{user.name}}!\"";
        const tokens = tokenizeMonarch(input);
        const significant = tokens.filter(t => t.token !== "white");

        // Dotted paths fall through to the invalid catch-all rule.
        const placeholderToken = significant.find(t => t.text.includes("user.name"));
        expect(placeholderToken).toBeDefined();
        expect(placeholderToken?.token).toBe("invalid");
    });
});

describe("PromptTemplateLanguage — Grammar Pattern Verification", () => {
    const VARIABLE_REGEX = /^\{\{\s*\w+\s*\}\}/;
    const IF_REGEX = /^\{\{\s*#if\s+[^{}]+\s*\}\}/;
    const UNLESS_REGEX = /^\{\{\s*#unless\s+[^{}]+\s*\}\}/;
    const ELSE_REGEX = /^\{\{\s*else\s*\}\}/;
    const THIS_REGEX = /^\{\{\s*this\s*\}\}/;
    const END_IF_REGEX = /^\{\{\s*\/if\s*\}\}/;
    const END_UNLESS_REGEX = /^\{\{\s*\/unless\s*\}\}/;
    const TRIPLE_BRACE_REGEX = /^\{\{\{.*?\}\}\}/;

    it("matches simple variable {{name}} and whitespace variations", () => {
        expect(VARIABLE_REGEX.test("{{name}}")).toBe(true);
        expect(VARIABLE_REGEX.test("{{ name }}")).toBe(true);
        expect(VARIABLE_REGEX.test("{{   name   }}")).toBe(true);
        expect(VARIABLE_REGEX.test("{{max_words}}")).toBe(true);
    });

    it("does NOT match dotted paths or hyphens as variables", () => {
        expect(VARIABLE_REGEX.test("{{user.name}}")).toBe(false);
        expect(VARIABLE_REGEX.test("{{my-var}}")).toBe(false);
    });

    it("matches #if, #unless, else, this, /if, /unless", () => {
        expect(IF_REGEX.test("{{#if premium}}")).toBe(true);
        expect(UNLESS_REGEX.test("{{#unless premium}}")).toBe(true);
        expect(ELSE_REGEX.test("{{else}}")).toBe(true);
        expect(THIS_REGEX.test("{{this}}")).toBe(true);
        expect(END_IF_REGEX.test("{{/if}}")).toBe(true);
        expect(END_UNLESS_REGEX.test("{{/unless}}")).toBe(true);
    });

    it("matches triple braces as invalid construct", () => {
        expect(TRIPLE_BRACE_REGEX.test("{{{raw}}}")).toBe(true);
        expect(TRIPLE_BRACE_REGEX.test("{{{ name }}}")).toBe(true);
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
