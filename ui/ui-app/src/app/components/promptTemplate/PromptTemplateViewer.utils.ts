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
