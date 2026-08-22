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

const escapeRegExp = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/**
 * Renders a template preview by substituting {{variable}} / {{{variable}}} / {variable}
 * placeholders with the supplied values via plain string replacement. A variable with no
 * value (undefined or "") is left unsubstituted so its placeholder stays visible in the
 * preview. Not Handlebars-aware beyond the brace matching already done by
 * extractPromptVariables — block helpers, conditionals, etc. are not evaluated.
 */
export const renderTemplatePreview = (template: string, values: Record<string, string>): string => {
    if (!template) {
        return "";
    }

    let output = template;
    Object.entries(values).forEach(([name, value]) => {
        if (!value) {
            return;
        }
        const escapedName = escapeRegExp(name);
        const doubleBrace = new RegExp(`\\{{2,3}\\s*${escapedName}\\s*\\}{2,3}`, "g");
        const singleBrace = new RegExp(`\\{\\s*${escapedName}\\s*\\}`, "g");
        output = output.replace(doubleBrace, value).replace(singleBrace, value);
    });
    return output;
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
