import type { Monaco } from "@monaco-editor/react";

export const PROMPT_TEMPLATE_LANGUAGE_ID = "prompt-template";

export const promptTemplateMonarchTokensProvider = {
    tokenizer: {
        root: [
            // Keep template expressions inside comments unhighlighted.
            [/#.*$/, "comment"],
            { include: "@template" },
            { include: "@whitespace" },

            // YAML-style keys
            [/^\s*[\w.-]+(?=\s*:)/, "type.identifier"],

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

            // Supported conditionals and keywords matching backend contract.
            [/\{\{\s*#if\s+[^{}]+\s*\}\}/, "keyword"],
            [/\{\{\s*#unless\s+[^{}]+\s*\}\}/, "keyword"],
            [/\{\{\s*else\s*\}\}/, "keyword"],
            [/\{\{\s*this\s*\}\}/, "keyword"],
            [/\{\{\s*\/if\s*\}\}/, "keyword"],
            [/\{\{\s*\/unless\s*\}\}/, "keyword"],

            // Support simple variable names only (dotted paths remain literal).
            [/\{\{\s*\w+\s*\}\}/, "variable"],

            // Other template expressions are not yet supported.
            [/\{\{.*?\}\}/, "invalid"]
        ],

        whitespace: [
            [/[ \t\r\n]+/, "white"],
            [/#.*$/, "comment"]
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
