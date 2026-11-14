import { isMap, isScalar, isSeq, LineCounter, Node, parseDocument } from "yaml";

/**
 * A range of text, using 1-based lines and columns (the same convention Monaco uses).
 */
export type TextRange = {
    startLine: number;
    startColumn: number;
    endLine: number;
    endColumn: number;
};

/**
 * Where a JSON Pointer landed in a piece of text.  When only part of the pointer could be
 * resolved, `exact` is false and `resolvedPointer` is the deepest prefix that was found.
 */
export type PointerLocation = {
    range: TextRange;
    resolvedPointer: string;
    exact: boolean;
};

/**
 * Splits a JSON Pointer (RFC 6901) into its unescaped reference tokens.  Also accepts the URI
 * fragment form ("#/a/b").  Returns undefined when the value is not a pointer.
 */
export function parsePointer(pointer: string): string[] | undefined {
    let value: string = pointer;
    if (value.startsWith("#")) {
        try {
            value = decodeURIComponent(value.substring(1));
        } catch {
            return undefined;
        }
    }
    if (value === "") {
        return [];
    }
    if (!value.startsWith("/")) {
        return undefined;
    }
    return value.substring(1).split("/").map(token => token.replace(/~1/g, "/").replace(/~0/g, "~"));
}

const toPointer = (tokens: string[]): string => {
    return tokens.map(token => "/" + token.replace(/~/g, "~0").replace(/\//g, "~1")).join("");
};

/**
 * Finds the text range that a JSON Pointer refers to in the given JSON or YAML text.  The text
 * should be exactly what is displayed to the user, since positions are computed on it.
 *
 * For an object member, the range covers the key and its value.  If a token cannot be resolved,
 * the deepest node that was resolved is returned instead, with `exact` set to false.  Returns
 * undefined if the text cannot be parsed or the value is not a pointer.
 */
export function locatePointer(text: string, pointer: string): PointerLocation | undefined {
    const tokens: string[] | undefined = parsePointer(pointer);
    if (tokens === undefined) {
        return undefined;
    }

    const lineCounter: LineCounter = new LineCounter();
    const doc = parseDocument(text, { lineCounter });
    if (doc.errors.length > 0 || !doc.contents || !doc.contents.range) {
        return undefined;
    }

    let node: unknown = doc.contents;
    let start: number = doc.contents.range[0];
    let end: number = doc.contents.range[1];
    let resolved: number = 0;

    for (const token of tokens) {
        let next: Node | undefined;
        let nextStart: number | undefined;
        if (isMap(node)) {
            const pair = node.items.find(item => isScalar(item.key) && String(item.key.value) === token);
            if (pair && isScalar(pair.key) && pair.key.range) {
                next = pair.value as Node | undefined;
                nextStart = pair.key.range[0];
            }
        } else if (isSeq(node) && /^(0|[1-9][0-9]*)$/.test(token)) {
            next = node.items[Number(token)] as Node | undefined;
            nextStart = next?.range?.[0];
        }
        if (!next || !next.range || nextStart === undefined) {
            break;
        }
        node = next;
        start = nextStart;
        end = next.range[1];
        resolved++;
    }

    // Block collections end after their trailing line break; keep the highlight on the content.
    while (end > start && /\s/.test(text.charAt(end - 1))) {
        end--;
    }

    const startPos = lineCounter.linePos(start);
    const endPos = lineCounter.linePos(end);
    return {
        range: {
            startLine: startPos.line,
            startColumn: startPos.col,
            endLine: endPos.line,
            endColumn: endPos.col
        },
        resolvedPointer: toPointer(tokens.slice(0, resolved)),
        exact: resolved === tokens.length
    };
}
