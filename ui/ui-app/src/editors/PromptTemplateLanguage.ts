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

export const PROMPT_TEMPLATE_MARKER_OWNER = "prompt-template";

export interface PromptTemplateIssue {
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
    message: string;
}

type BlockKind = "if" | "unless";

interface OpenBlock {
    kind: BlockKind;
    hasElse: boolean;
    range: Omit<PromptTemplateIssue, "message">;
}

interface TemplateTag {
    value: string;
    range: Omit<PromptTemplateIssue, "message">;
    invalid: boolean;
}

// Mirrors the template state of the tokenizer, used to split merged adjacent tokens into tags.
const TAG_PATTERN = /\{\{\{.*?\}\}\}|\{\{.*?\}\}/g;
const OPEN_TAG_PATTERN = /^\{\{#(if|unless)\s+\w+\}\}$/;
const CLOSE_TAG_PATTERN = /^\{\{\/(if|unless)\}\}$/;
const TOKEN_TYPE_SUFFIX = `.${PROMPT_TEMPLATE_LANGUAGE_ID}`;

const describeUnsupportedTag = (value: string): string => {
    if (value.startsWith("{{{")) {
        return "Triple-brace expressions are not supported by the prompt renderer.";
    }
    if (/^\{\{\s*this\s*\}\}$/.test(value)) {
        return "{{this}} is not supported: the prompt renderer has no {{#each}} block, so it is left as literal text.";
    }
    if (/^\{\{\s*else\s*\}\}$/.test(value)) {
        return "Write {{else}} without inner spaces; this form is left as literal text.";
    }
    if (/^\{\{\s*[#/]/.test(value)) {
        return "Unsupported block tag. Supported forms are {{#if name}}, {{#unless name}}, {{else}}, {{/if}} and {{/unless}}.";
    }
    return "Unsupported template expression. Use a simple variable such as {{name}}.";
};

// Next `needle` at or after (lineIndex, column), searching across lines.
const findForward = (lines: string[], lineIndex: number, column: number, needle: string) => {
    for (let index = lineIndex; index < lines.length; index++) {
        const found = lines[index].indexOf(needle, index === lineIndex ? column : 0);
        if (found >= 0) {
            return { lineIndex: index, column: found };
        }
    }
    return undefined;
};

/**
 * Collects `{{` tags that continue on a later line, which the line-based tokenizer cannot see.
 * The renderer accepts newlines inside some tags, so they are reported rather than ignored.
 */
const findMultiLineTags = (lines: string[], lineIndex: number, covered: Array<[number, number]>): TemplateTag[] => {
    const tags: TemplateTag[] = [];
    const lineText = lines[lineIndex];
    const openPattern = /\{\{/g;
    let match: RegExpExecArray | null;
    while ((match = openPattern.exec(lineText)) !== null) {
        const start = match.index;
        if (covered.some(([from, to]) => start >= from && start < to) || lineText.indexOf("}}", start + 2) >= 0) {
            continue;
        }
        const close = findForward(lines, lineIndex + 1, 0, "}}");
        if (!close) {
            continue;
        }
        const nextOpen = findForward(lines, lineIndex + 1, 0, "{{");
        if (nextOpen && (nextOpen.lineIndex < close.lineIndex
            || (nextOpen.lineIndex === close.lineIndex && nextOpen.column < close.column))) {
            continue;
        }
        const value = [lineText.slice(start), ...lines.slice(lineIndex + 1, close.lineIndex),
            lines[close.lineIndex].slice(0, close.column + 2)].join("\n");
        tags.push({
            value,
            invalid: false,
            range: {
                startLineNumber: lineIndex + 1,
                startColumn: start + 1,
                endLineNumber: close.lineIndex + 1,
                endColumn: close.column + 3
            }
        });
    }
    return tags;
};

const checkBlockTag = (tag: TemplateTag, stack: OpenBlock[], issues: PromptTemplateIssue[]): void => {
    const open = OPEN_TAG_PATTERN.exec(tag.value);
    if (open) {
        if (stack.length > 0) {
            issues.push({ ...tag.range, message: "Nested conditional blocks are not supported by the prompt renderer." });
        }
        stack.push({ kind: open[1] as BlockKind, hasElse: false, range: tag.range });
        return;
    }

    const top = stack[stack.length - 1];
    if (tag.value === "{{else}}") {
        if (!top || top.kind !== "if") {
            issues.push({ ...tag.range, message: "{{else}} is only supported inside an {{#if}} block; here it is left as literal text." });
        } else if (top.hasElse) {
            issues.push({ ...tag.range, message: "Only one {{else}} is supported per {{#if}} block; this one is left as literal text." });
        } else {
            top.hasElse = true;
        }
        return;
    }

    const close = CLOSE_TAG_PATTERN.exec(tag.value);
    if (close) {
        if (top && top.kind === close[1]) {
            stack.pop();
        } else {
            issues.push({ ...tag.range, message: `{{/${close[1]}}} has no matching {{#${close[1]}}}; it is left as literal text.` });
        }
    }
};

/**
 * Reports constructs PromptRenderingService does not support: unsupported expressions, nested,
 * unclosed or unmatched blocks, misplaced {{else}}, and multi-line tags. Reusing the tokenizer keeps
 * results consistent with highlighting, skips YAML comments, and tolerates invalid YAML/JSON.
 */
export const findPromptTemplateIssues = (monaco: Monaco, text: string): PromptTemplateIssue[] => {
    const lines = text.split(/\r\n|\r|\n/);
    const tokenLines = monaco.editor.tokenize(text, PROMPT_TEMPLATE_LANGUAGE_ID);
    const issues: PromptTemplateIssue[] = [];
    const stack: OpenBlock[] = [];

    tokenLines.forEach((tokens: Array<{ offset: number; type: string }>, lineIndex: number) => {
        const lineText = lines[lineIndex] ?? "";
        const covered: Array<[number, number]> = [];
        const tags: TemplateTag[] = [];

        tokens.forEach((token, tokenIndex) => {
            const start = token.offset;
            const end = tokenIndex + 1 < tokens.length ? tokens[tokenIndex + 1].offset : lineText.length;
            const type = token.type.endsWith(TOKEN_TYPE_SUFFIX) ? token.type.slice(0, -TOKEN_TYPE_SUFFIX.length) : token.type;
            if (type !== "comment" && type !== "variable" && type !== "keyword" && type !== "invalid") {
                return;
            }
            covered.push([start, end]);
            if (type === "keyword" || type === "invalid") {
                for (const match of lineText.slice(start, end).matchAll(TAG_PATTERN)) {
                    const column = start + (match.index ?? 0) + 1;
                    tags.push({
                        value: match[0],
                        invalid: type === "invalid",
                        range: { startLineNumber: lineIndex + 1, startColumn: column, endLineNumber: lineIndex + 1, endColumn: column + match[0].length }
                    });
                }
            }
        });

        for (const tag of findMultiLineTags(lines, lineIndex, covered)) {
            issues.push({ ...tag.range, message: "Template tag spans multiple lines. The editor only recognizes tags written on one line." });
            tags.push(tag);
        }

        tags.sort((a, b) => a.range.startColumn - b.range.startColumn)
            .forEach(tag => tag.invalid
                ? issues.push({ ...tag.range, message: describeUnsupportedTag(tag.value) })
                : checkBlockTag(tag, stack, issues));
    });

    for (const block of stack) {
        issues.push({ ...block.range, message: `Unclosed {{#${block.kind}}} block; the prompt renderer leaves it as literal text.` });
    }
    return issues;
};

export const registerPromptTemplate = (monaco: Monaco): void => {
    // Avoid duplicate registrations on the same Monaco instance.
    if (monaco.languages.getLanguages().some((lang: { id: string }) => lang.id === PROMPT_TEMPLATE_LANGUAGE_ID)) {
        return;
    }

    monaco.languages.register({ id: PROMPT_TEMPLATE_LANGUAGE_ID });
    monaco.languages.setMonarchTokensProvider(PROMPT_TEMPLATE_LANGUAGE_ID, promptTemplateMonarchTokensProvider);
    monaco.languages.setLanguageConfiguration(PROMPT_TEMPLATE_LANGUAGE_ID, createPromptTemplateLanguageConfiguration(monaco));
};
