import { RuleViolationCause, RuleViolationProblemDetails } from "@sdk/lib/generated-client/models";

/**
 * Where a difference is, in one document. `reference` is set when the pointer is into a referenced
 * artifact rather than the document itself (see #10297, Design -> REST API).
 */
export interface DifferenceLocation {
    pointer?: string;
    reference?: string;
    version?: string;
}

/** The content a user submitted, kept so it can be shown with the rule violations it caused. */
export interface SubmittedContent {
    content?: string;
    contentType?: string;
}

/** Keeps the content and content type of a create or test request body (an SDK `VersionContent`). */
export const toSubmittedContent = (
    content: { content?: string | null, contentType?: string | null } | null | undefined
): SubmittedContent => {
    return {
        content: content?.content ?? undefined,
        contentType: content?.contentType ?? undefined
    };
};

export interface ViolationCause {
    description: string;
    context?: string;
    code?: string;
    direction?: string;
    proposed?: DifferenceLocation;
    existing?: DifferenceLocation;
}

export interface ViolationGroup {
    /** The location the causes share, or undefined for causes with no location. */
    location?: string;
    /** Set when the location is in a referenced artifact, so it must not be applied to the submitted document. */
    reference?: string;
    causes: ViolationCause[];
}

type WithAdditionalData = { additionalData?: Record<string, unknown> };

/**
 * Reads a field that the generated SDK may not know about yet. Kiota keeps unknown properties in
 * `additionalData`, so a field added to the API before the SDK is regenerated lands there.
 */
const readField = (source: unknown, key: string): unknown => {
    if (source === null || typeof source !== "object") {
        return undefined;
    }
    const direct: unknown = (source as Record<string, unknown>)[key];
    if (direct !== undefined && direct !== null) {
        return direct;
    }
    return (source as WithAdditionalData).additionalData?.[key];
};

const readString = (source: unknown, key: string): string | undefined => {
    const value: unknown = readField(source, key);
    return typeof value === "string" && value.length > 0 ? value : undefined;
};

const readLocation = (source: unknown, key: string): DifferenceLocation | undefined => {
    const value: unknown = readField(source, key);
    if (value === null || typeof value !== "object") {
        return undefined;
    }
    const pointer: unknown = readField(value, "pointer");
    const location: DifferenceLocation = {
        // "" is a valid JSON Pointer: the whole document.
        pointer: typeof pointer === "string" ? pointer : undefined,
        reference: readString(value, "reference"),
        version: readString(value, "version")
    };
    return location.pointer !== undefined || location.reference !== undefined || location.version !== undefined
        ? location
        : undefined;
};

/**
 * The rule that was violated, from the `ruleType` field (VALIDITY, COMPATIBILITY, INTEGRITY), when the
 * server sends it.
 */
export const ruleTypeOf = (error: RuleViolationProblemDetails | undefined): string | undefined => {
    return readString(error, "ruleType");
};

/**
 * Whether the error is a rule violation with causes to list. Uses `ruleType` when present, and falls
 * back to the exception name otherwise, which is the check the UI has always made (#9657 changes
 * `name`, so it cannot be the only signal).
 */
export const isRuleViolation = (error: RuleViolationProblemDetails | undefined): boolean => {
    if (!error || !Array.isArray(error.causes) || error.causes.length === 0) {
        return false;
    }
    return ruleTypeOf(error) !== undefined || error.nameEscaped === "RuleViolationException";
};

/**
 * Whether the error should be shown as a compatibility report. With `ruleType` this is exact. Without it,
 * any rule violation qualifies: the report then shows the same rows as the plain list, so validity
 * violations look exactly as they did before.
 */
export const isCompatibilityViolation = (error: RuleViolationProblemDetails | undefined): boolean => {
    if (!isRuleViolation(error)) {
        return false;
    }
    const ruleType: string | undefined = ruleTypeOf(error);
    return ruleType === undefined || ruleType === "COMPATIBILITY";
};

export const toViolationCause = (cause: RuleViolationCause): ViolationCause => {
    return {
        description: cause.description ?? "",
        context: cause.context ?? undefined,
        code: readString(cause, "code"),
        direction: readString(cause, "direction"),
        proposed: readLocation(cause, "proposed"),
        existing: readLocation(cause, "existing")
    };
};

/**
 * Groups causes that share a location in the submitted content (`proposed.pointer`). A location in a
 * referenced artifact is keyed by the reference too, so it is never merged with the same pointer in the
 * document. A cause without `proposed` keeps a row of its own, titled by its `context`, which is exactly
 * how the plain cause list shows it. Groups keep the order of their first cause.
 */
export const groupCausesByLocation = (causes: ViolationCause[]): ViolationGroup[] => {
    const groups: ViolationGroup[] = [];
    const byKey: Map<string, ViolationGroup> = new Map();
    causes.forEach(cause => {
        const pointer: string | undefined = cause.proposed?.pointer;
        if (pointer === undefined) {
            groups.push({ location: cause.context, reference: cause.proposed?.reference, causes: [cause] });
            return;
        }
        const reference: string | undefined = cause.proposed?.reference;
        const key: string = JSON.stringify([reference ?? null, pointer]);
        let group: ViolationGroup | undefined = byKey.get(key);
        if (!group) {
            group = { location: pointer, reference, causes: [] };
            byKey.set(key, group);
            groups.push(group);
        }
        group.causes.push(cause);
    });
    return groups;
};

/**
 * The existing version the causes were compared against, when the server reports one. Non-transitive
 * levels compare against a single version, so the first one found is the one to show.
 */
export const comparedVersionOf = (causes: ViolationCause[]): string | undefined => {
    // A version on a location in a referenced artifact is that artifact's version, not this one's.
    return causes.find(cause => cause.existing?.version !== undefined && cause.existing.reference === undefined)
        ?.existing?.version;
};
