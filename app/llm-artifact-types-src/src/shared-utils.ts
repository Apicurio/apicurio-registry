import { parse as parseYaml } from 'yaml';

/**
 * Shared type for content with type information
 */
export type TypedContent = {
    contentType: string;
    content: string;
}

/**
 * Parse content as JSON or YAML based on content type.
 * Returns null if parsing fails.
 */
export function parseContent<T>(content: string, contentType: string): T | null {
    try {
        if (contentType === 'application/json') {
            return JSON.parse(content) as T;
        } else if (contentType === 'application/x-yaml' ||
                   contentType === 'application/yaml' ||
                   contentType === 'text/x-prompt-template') {
            return parseYaml(content) as T;
        }
        // Try JSON first, then YAML
        try {
            return JSON.parse(content) as T;
        } catch {
            return parseYaml(content) as T;
        }
    } catch {
        return null;
    }
}

/**
 * Sort object keys recursively for canonicalization
 */
export function sortObjectKeys(obj: unknown): unknown {
    if (obj === null || typeof obj !== 'object') {
        return obj;
    }
    if (Array.isArray(obj)) {
        return obj.map(sortObjectKeys);
    }
    const sorted: Record<string, unknown> = {};
    const keys = Object.keys(obj as Record<string, unknown>).sort();
    for (const key of keys) {
        sorted[key] = sortObjectKeys((obj as Record<string, unknown>)[key]);
    }
    return sorted;
}

/**
 * Find all $ref references in an object recursively.
 * Only finds external references (not internal #/... references).
 */
export function findRefs(obj: unknown, refs: string[]): void {
    if (obj === null || typeof obj !== 'object') {
        return;
    }
    if (Array.isArray(obj)) {
        for (const item of obj) {
            findRefs(item, refs);
        }
        return;
    }
    const record = obj as Record<string, unknown>;
    if (typeof record.$ref === 'string') {
        const ref = record.$ref;
        // Only add external references (not internal #/...)
        if (!ref.startsWith('#') && !refs.includes(ref)) {
            refs.push(ref);
        }
    }
    for (const key of Object.keys(record)) {
        findRefs(record[key], refs);
    }
}

/**
 * Resolve $ref references in an object recursively
 */
export function resolveRefs<T>(
    obj: unknown,
    indexedRefs: Record<string, TypedContent>,
    parser: (content: string, contentType: string) => T | null
): unknown {
    if (obj === null || typeof obj !== 'object') {
        return obj;
    }
    if (Array.isArray(obj)) {
        return obj.map(item => resolveRefs(item, indexedRefs, parser));
    }
    const record = obj as Record<string, unknown>;
    if (typeof record.$ref === 'string') {
        const ref = record.$ref;
        // Resolve external references
        if (!ref.startsWith('#') && indexedRefs[ref]) {
            try {
                const resolved = parser(indexedRefs[ref].content, indexedRefs[ref].contentType);
                if (resolved) {
                    return resolved;
                }
            } catch {
                // Keep original ref if parse fails
            }
        }
    }
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(record)) {
        result[key] = resolveRefs(record[key], indexedRefs, parser);
    }
    return result;
}

/**
 * Rewrite $ref values to use resolved URLs
 */
export function rewriteRefsWithUrls(obj: unknown, indexedUrls: Record<string, string>): unknown {
    if (obj === null || typeof obj !== 'object') {
        return obj;
    }
    if (Array.isArray(obj)) {
        return obj.map(item => rewriteRefsWithUrls(item, indexedUrls));
    }
    const record = obj as Record<string, unknown>;
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(record)) {
        if (key === '$ref' && typeof record[key] === 'string') {
            const ref = record[key] as string;
            if (!ref.startsWith('#') && indexedUrls[ref]) {
                result[key] = indexedUrls[ref];
            } else {
                result[key] = ref;
            }
        } else {
            result[key] = rewriteRefsWithUrls(record[key], indexedUrls);
        }
    }
    return result;
}
