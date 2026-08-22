const SINGLE_BRACE_VARIABLE = /\{\s*([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*\}/g;

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

export const formatRange = (minimum?: number, maximum?: number): string | null => {
    const hasMin = Number.isFinite(minimum);
    const hasMax = Number.isFinite(maximum);
    if (hasMin && hasMax) {
        if ((minimum as number) > (maximum as number)) return `≥ ${minimum}, ≤ ${maximum}`;
        return `${minimum} – ${maximum}`;
    }
    if (hasMin) return `≥ ${minimum}`;
    if (hasMax) return `≤ ${maximum}`;
    return null;
};