import React from "react";

export type TemplateTokenKind = "plain" | "variable" | "block";

export interface TemplateToken {
    text: string;
    kind: TemplateTokenKind;
}

const classifyTag = (tag: string): "variable" | "block" => {
    const inner = tag.replace(/^\{+|\}+$/g, "").trim();
    const head = inner.split(/\s+/, 1)[0];
    if (head.startsWith("#") || head.startsWith("/") || head.startsWith("^") || head === "else") {
        return "block";
    }
    return "variable";
};

const tagCloserAt = (template: string, openerIndex: number): { closer: string; openerLength: number } => {
    if (template.startsWith("{{!--", openerIndex)) {
        return { closer: "--}}", openerLength: 5 };
    }
    if (template.startsWith("{{!", openerIndex)) {
        return { closer: "}}", openerLength: 3 };
    }
    if (template.startsWith("{{{", openerIndex)) {
        return { closer: "}}}", openerLength: 3 };
    }
    return { closer: "}}", openerLength: 2 };
};

export const tokenizeTemplate = (template: string): TemplateToken[] => {
    const tokens: TemplateToken[] = [];
    let lastIndex = 0;
    let searchFrom = 0;

    while (searchFrom < template.length) {
        const openerIndex = template.indexOf("{{", searchFrom);
        if (openerIndex === -1) {
            break;
        }

        const { closer, openerLength } = tagCloserAt(template, openerIndex);
        const closerIndex = template.indexOf(closer, openerIndex + openerLength);
        if (closerIndex === -1) {
            break;
        }

        if (openerIndex > lastIndex) {
            tokens.push({ text: template.substring(lastIndex, openerIndex), kind: "plain" });
        }
        const tag = template.substring(openerIndex, closerIndex + closer.length);
        const kind = tag.startsWith("{{!") ? "plain" : classifyTag(tag);
        tokens.push({ text: tag, kind });
        lastIndex = closerIndex + closer.length;
        searchFrom = lastIndex;
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
