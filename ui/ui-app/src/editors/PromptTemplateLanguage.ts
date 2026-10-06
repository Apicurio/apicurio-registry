import type { Monaco } from "@monaco-editor/react";

export const PROMPT_TEMPLATE_LANGUAGE_ID = "prompt-template";

export const promptTemplateMonarchTokensProvider = {
    tokenizer: {
        root: [
            // Keep template expressions inside comments unhighlighted.
            [/#.*$/, "comment"],
            { include: "@template" },
            { include: "@whitespace" },

            // YAML block scalars (| and >)
            [/[|>](?:[+-]?[1-9]?|[1-9]?[+-]?)(?:\s*#.*)?$/, "operator", "@blockScalarStart"],

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
