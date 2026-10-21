import type { Monaco } from "@monaco-editor/react";

export const PROMPT_TEMPLATE_LANGUAGE_ID = "prompt-template";

// Explicit indentation indicators (1-9) in YAML block scalars take precedence
// over indentation inferred from the first content line. The required content
// indentation is parent indentation ($1) plus N spaces.
const explicitBlockScalarRules = [1, 2, 3, 4, 5, 6, 7, 8, 9].flatMap((n) => {
    const spaces = " ".repeat(n);
    const indicatorPattern = `(?:[+-]?${n}|${n}[+-]?)`;
    return [
        // Key with block scalar: e.g. `template: |2`
        [
            new RegExp(`^(\\s*)([\\w.-]+|"[^"]*"|'[^']*')(:)(\\s*)([|>]${indicatorPattern}\\s*(?:#.*)?)$`),
            ["white", "type.identifier", "delimiter", "white", { token: "operator", next: `@blockScalarBody.$1${spaces}` }]
        ],
        // Sequence item: e.g. `- |2`
        [
            new RegExp(`^(\\s*)(-)(\\s*)([|>]${indicatorPattern}\\s*(?:#.*)?)$`),
            ["white", "operator", "white", { token: "operator", next: `@blockScalarBody.$1${spaces}` }]
        ],
        // Standalone block scalar: e.g. `|2`
        [
            new RegExp(`^(\\s*)([|>]${indicatorPattern}\\s*(?:#.*)?)$`),
            ["white", { token: "operator", next: `@blockScalarBody.$1${spaces}` }]
        ]
    ];
});

export const promptTemplateMonarchTokensProvider = {
    tokenizer: {
        root: [
            // Explicit YAML indentation indicator block scalars (|2, >2, etc.)
            // must match at pos === 0 before whitespace consumes leading parent indentation.
            ...explicitBlockScalarRules,

            // Keep template expressions inside comments unhighlighted.
            [/#.*$/, "comment"],
            { include: "@template" },
            { include: "@whitespace" },

            // YAML block scalars with inferred indentation (| and >)
            [/[|>](?:[+-]?[1-9]?|[1-9]?[+-]?)\s*(?:#.*)?$/, "operator", "@blockScalarStart"],

            // YAML-style keys
            [/[\w.-]+(?=\s*:)/, "type.identifier"],

            // Quoted strings
            [/"/, { token: "string.quote", next: "@doubleString" }],
            [/'/, { token: "string.quote", next: "@singleString" }],

            // YAML/JSON-style punctuation
            [/[{}[\]]/, "@brackets"],
            [/[,:]/, "delimiter"],
            [/-\s+/, "operator"],

            // Numbers and booleans
            [/\b(?:true|false|null)\b/, "keyword"],
            [/-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/, "number"],

            // Plain scalar text
            [/[^\s{}[\],:#'"]+/, "string"],
            [/\s+/, "white"],
            [/[{}[\],:]/, "delimiter"]
        ],

        template: [
            // Triple-brace expressions are unsupported.
            [/\{\{\{.*?\}\}\}/, "invalid"],

            // Supported conditionals and keywords matching backend PromptRenderingService contract.
            [/\{\{#if\s+\w+\}\}/, "keyword"],
            [/\{\{#unless\s+\w+\}\}/, "keyword"],
            [/\{\{else\}\}/, "keyword"],
            [/\{\{this\}\}/, "keyword"],
            [/\{\{\/if\}\}/, "keyword"],
            [/\{\{\/unless\}\}/, "keyword"],

            // Support simple variable names only (dotted paths remain literal).
            [/\{\{\s*\w+\s*\}\}/, "variable"],

            // Other template expressions are not yet supported.
            [/\{\{.*?\}\}/, "invalid"]
        ],

        whitespace: [
            [/[ \t\r\n]+/, "white"],
            [/#.*$/, "comment"]
        ],

        blockScalarStart: [
            [/^[ \t]*$/, "white"],
            [/^( +)/, { token: "white", switchTo: "@blockScalarBody.$1" }],
            [/^/, { token: "@rematch", next: "@pop" }]
        ],

        blockScalarBody: [
            [/^[ \t]*$/, "white"],
            [
                /^( *)(?=\S)/,
                {
                    cases: {
                        "$1~$S2.*": { token: "white" },
                        "@default": { token: "@rematch", next: "@pop" }
                    }
                }
            ],
            { include: "@template" },
            [/[^{}]+/, "string"],
            [/[{}]/, "string"]
        ],

        doubleString: [
            { include: "@template" },
            [/\\./, "string.escape"],
            [/[^\\"]+?(?=\{\{|")/, "string"],
            [/[^\\"]+/, "string"],
            [/"/, { token: "string.quote", next: "@pop" }]
        ],

        singleString: [
            { include: "@template" },
            [/\\./, "string.escape"],
            [/[^']+?(?=\{\{|')/, "string"],
            [/[^']+/, "string"],
            [/'/, { token: "string.quote", next: "@pop" }]
        ]
    }
};

export const registerPromptTemplate = (monaco: Monaco): void => {
    // Avoid duplicate registrations on the same Monaco instance.
    if (monaco.languages.getLanguages().some((lang: { id: string }) => lang.id === PROMPT_TEMPLATE_LANGUAGE_ID)) {
        return;
    }

    monaco.languages.register({ id: PROMPT_TEMPLATE_LANGUAGE_ID });
    monaco.languages.setMonarchTokensProvider(PROMPT_TEMPLATE_LANGUAGE_ID, promptTemplateMonarchTokensProvider);
};
