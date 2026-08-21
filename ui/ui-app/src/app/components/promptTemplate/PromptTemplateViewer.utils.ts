import React from "react";

export type TemplateTokenKind = "plain" | "variable" | "block";

export interface TemplateToken {
    text: string;
    kind: TemplateTokenKind;
}

const HANDLEBARS_TAG = /\{\{!--[\s\S]*?--\}\}|\{\{![\s\S]*?\}\}|\{\{\{[\s\S]*?\}\}\}|\{\{[\s\S]*?\}\}/g;

const classifyTag = (tag: string): "variable" | "block" => {
    const inner = tag.replace(/^\{+|\}+$/g, "").trim();
    const head = inner.split(/\s+/, 1)[0];
    if (head.startsWith("#") || head.startsWith("/") || head.startsWith("^") || head === "else") {
        return "block";
    }
    return "variable";
};

export const tokenizeTemplate = (template: string): TemplateToken[] => {
    const tokens: TemplateToken[] = [];
    let lastIndex = 0;
    let match;
    while ((match = HANDLEBARS_TAG.exec(template)) !== null) {
        if (match.index > lastIndex) {
            tokens.push({ text: template.substring(lastIndex, match.index), kind: "plain" });
        }
        const tag = match[0];
        const kind = tag.startsWith("{{!") ? "plain" : classifyTag(tag);
        tokens.push({ text: tag, kind });
        lastIndex = match.index + match[0].length;
    }
    if (lastIndex < template.length) {
        tokens.push({ text: template.substring(lastIndex), kind: "plain" });
    }
    return tokens;
};

const SINGLE_BRACE_VARIABLE = /\{\s*([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*\}/g;

/**
 * Extracts the unique variable placeholder names referenced by a prompt template.
 * Supports both {{variable}} (Mustache/Handlebars-style, via tokenizeTemplate) and
 * bare {variable} syntax. Block helpers (e.g. {{#if}}) and comments are excluded.
 * Single-brace matches are scanned only within the plain-text spans left over after
 * double-brace tags are extracted, so they can't overlap a {{...}} tag.
 */
export const extractPromptVariables = (content: string): string[] => {
    if (!content) {
        return [];
    }

    const names = new Set<string>();

    tokenizeTemplate(content).forEach((token) => {
        if (token.kind === "variable") {
            const inner = token.text.replace(/^\{+|\}+$/g, "").trim();
            const head = inner.split(/\s+/, 1)[0];
            if (head) {
                names.add(head);
            }
        } else if (token.kind === "plain") {
            let match;
            SINGLE_BRACE_VARIABLE.lastIndex = 0;
            while ((match = SINGLE_BRACE_VARIABLE.exec(token.text)) !== null) {
                names.add(match[1]);
            }
        }
    });

    return Array.from(names);
};

export const highlightVariables = (template: string): React.ReactNode[] => {
    return tokenizeTemplate(template).map((token, index) => {
        if (token.kind === "plain") {
            return token.text;
        }
        return React.createElement(
            "span",
            { key: index, className: token.kind === "block" ? "template-block" : "template-variable" },
            token.text
        );
    });
};
