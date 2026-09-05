import React from "react";
import { VariableSchema } from "./promptTemplateVariables";

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

const findTagCloser = (
    template: string,
    openerIndex: number,
    closer: string,
    openerLength: number
): { closerIndex: number; closerLength: number } | null => {
    const searchFrom = openerIndex + openerLength;
    const preferredIndex = template.indexOf(closer, searchFrom);
    if (preferredIndex !== -1) {
        return { closerIndex: preferredIndex, closerLength: closer.length };
    }
    // Match the old regex fallback: {{!-- / {{{ still close on }} when the preferred terminator is missing.
    if (closer !== "}}") {
        const fallbackIndex = template.indexOf("}}", searchFrom);
        if (fallbackIndex !== -1) {
            return { closerIndex: fallbackIndex, closerLength: 2 };
        }
    }
    return null;
};

export const tokenizeTemplate = (template: string): TemplateToken[] => {
    const tokens: TemplateToken[] = [];
    let lastIndex = 0;

    while (lastIndex < template.length) {
        const openerIndex = template.indexOf("{{", lastIndex);
        if (openerIndex === -1) {
            break;
        }

        const { closer, openerLength } = tagCloserAt(template, openerIndex);
        const found = findTagCloser(template, openerIndex, closer, openerLength);
        if (found === null) {
            break;
        }

        if (openerIndex > lastIndex) {
            tokens.push({ text: template.substring(lastIndex, openerIndex), kind: "plain" });
        }
        const tagEnd = found.closerIndex + found.closerLength;
        const tag = template.substring(openerIndex, tagEnd);
        const kind = tag.startsWith("{{!") ? "plain" : classifyTag(tag);
        tokens.push({ text: tag, kind });
        lastIndex = tagEnd;
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

export const getVariablesList = (variables: Record<string, VariableSchema> | VariableSchema[] | undefined): { name: string; variable: VariableSchema }[] => {
    if (!variables) return [];
    if (Array.isArray(variables)) {
        return variables.map(v => ({ name: v.name || "", variable: v }));
    }
    return Object.entries(variables).map(([name, variable]) => ({ name, variable }));
};

// Format a variable default for display in the Variables table.
// Objects and arrays go through JSON.stringify so they don't render as "[object Object]".
export const formatDefault = (value: any): string => {
    if (typeof value === "object" && value !== null) {
        return JSON.stringify(value);
    }
    return String(value);
};
