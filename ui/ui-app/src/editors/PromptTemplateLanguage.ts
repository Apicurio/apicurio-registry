import type { Monaco } from "@monaco-editor/react";
import type { languages } from "monaco-editor";

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

            // A YAML comment needs a preceding space or line start; `#` in `C#` or `Issue#42` is text.
            [/^\s*#.*$/, "comment"],
            [/(\s+)(#.*$)/, ["white", "comment"]],
            { include: "@template" },
            { include: "@whitespace" },

            // YAML block scalars with inferred indentation (| and >)
            [/[|>](?:[+-]?[1-9]?|[1-9]?[+-]?)\s*(?:#.*)?$/, "operator", "@blockScalarStart"],

            [/[\w.-]+(?=\s*:)/, "type.identifier"],

            [/"/, { token: "string.quote", next: "@doubleString" }],
            [/'/, { token: "string.quote", next: "@singleString" }],

            [/[{}[\]]/, "@brackets"],
            [/[,:]/, "delimiter"],
            [/-(?=\s|$)/, "operator"],

            [/\b(?:true|false|null)\b/, "keyword"],
            [/-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/, "number"],

            [/[^\s{}[\],:'"]+/, "string"],
            [/\s+/, "white"],
            [/[{}[\],:]/, "delimiter"]
        ],

        template: [
            [/\{\{\{.*?\}\}\}/, "invalid"],

            // Conditionals supported by the backend PromptRenderingService.
            [/\{\{#if\s+\w+\}\}/, "keyword"],
            [/\{\{#unless\s+\w+\}\}/, "keyword"],
            [/\{\{else\}\}/, "keyword"],
            [/\{\{\/if\}\}/, "keyword"],
            [/\{\{\/unless\}\}/, "keyword"],

            // The backend never substitutes control words: `{{this}}` has no `{{#each}}` to bind to,
            // and `{{ else }}` is only recognized without inner whitespace.
            [/\{\{\s*(?:else|this)\s*\}\}/, "invalid"],

            // Support simple variable names only (dotted paths remain literal).
            [/\{\{\s*\w+\s*\}\}/, "variable"],

            [/\{\{.*?\}\}/, "invalid"]
        ],

        whitespace: [
            [/[ \t\r\n]+/, "white"]
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

// Editing behavior for the host YAML/JSON document. Auto-closing `{` makes typing `{{` yield `{{}}`.
export const createPromptTemplateLanguageConfiguration = (monaco: Monaco): languages.LanguageConfiguration => ({
    comments: { lineComment: "#" },
    brackets: [["{", "}"], ["[", "]"]],
    autoClosingPairs: [
        { open: "{", close: "}" },
        { open: "[", close: "]" },
        { open: "\"", close: "\"", notIn: ["string"] },
        { open: "'", close: "'", notIn: ["string", "comment"] }
    ],
    surroundingPairs: [
        { open: "{", close: "}" },
        { open: "[", close: "]" },
        { open: "\"", close: "\"" },
        { open: "'", close: "'" }
    ],
    folding: { offSide: true },
    onEnterRules: [
        {
            beforeText: /:\s*(?:[|>][-+0-9]*)?\s*(?:#.*)?$/,
            action: { indentAction: monaco.languages.IndentAction.Indent }
        }
    ]
});

export const registerPromptTemplate = (monaco: Monaco): void => {
    // Avoid duplicate registrations on the same Monaco instance.
    if (monaco.languages.getLanguages().some((lang: { id: string }) => lang.id === PROMPT_TEMPLATE_LANGUAGE_ID)) {
        return;
    }

    monaco.languages.register({ id: PROMPT_TEMPLATE_LANGUAGE_ID });
    monaco.languages.setMonarchTokensProvider(PROMPT_TEMPLATE_LANGUAGE_ID, promptTemplateMonarchTokensProvider);
    monaco.languages.setLanguageConfiguration(PROMPT_TEMPLATE_LANGUAGE_ID, createPromptTemplateLanguageConfiguration(monaco));
};
