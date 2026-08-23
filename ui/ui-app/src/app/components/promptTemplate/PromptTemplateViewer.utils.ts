import React from "react";
import { classifyHandlebarsTag } from "./promptTemplateVariables";

export type TemplateTokenKind = "plain" | "variable" | "block";

export interface TemplateToken {
    text: string;
    kind: TemplateTokenKind;
}

const HANDLEBARS_TAG = /\{\{!--[\s\S]*?--\}\}|\{\{![\s\S]*?\}\}|\{\{\{[\s\S]*?\}\}\}|\{\{[\s\S]*?\}\}/g;

export const tokenizeTemplate = (template: string): TemplateToken[] => {
    const tokens: TemplateToken[] = [];
    let lastIndex = 0;
    let match;
    while ((match = HANDLEBARS_TAG.exec(template)) !== null) {
        if (match.index > lastIndex) {
            tokens.push({ text: template.substring(lastIndex, match.index), kind: "plain" });
        }
        const tag = match[0];
        const kind = tag.startsWith("{{!") ? "plain" : classifyHandlebarsTag(tag);
        tokens.push({ text: tag, kind });
        lastIndex = match.index + match[0].length;
    }
    if (lastIndex < template.length) {
        tokens.push({ text: template.substring(lastIndex), kind: "plain" });
    }
    return tokens;
};

export const formatRange = (minimum?: number, maximum?: number): string | null => {
    const hasMin = Number.isFinite(minimum);
    const hasMax = Number.isFinite(maximum);
    if (hasMin && hasMax) {
        // Malformed schema (min > max): render bounds separately so the
        // contradiction is visible instead of a nonsensical inverted range.
        if ((minimum as number) > (maximum as number)) return `≥ ${minimum}, ≤ ${maximum}`;
        return `${minimum} – ${maximum}`;
    }
    if (hasMin) return `≥ ${minimum}`;
    if (hasMax) return `≤ ${maximum}`;
    return null;
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
