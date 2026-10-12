import { describe, expect, it } from "vitest";
import { RuleViolationProblemDetails } from "@sdk/lib/generated-client/models";
import {
    comparedVersionOf,
    groupCausesByLocation,
    isCompatibilityViolation,
    causesOf,
    isRuleViolation,
    ruleTypeOf,
    toSubmittedContent,
    toViolationCause,
    ViolationCause
} from "./ruleViolation.utils";

const violation = (fields: Record<string, unknown>): RuleViolationProblemDetails =>
    ({ causes: [{ description: "d", context: "/c" }], ...fields }) as RuleViolationProblemDetails;

const cause = (description: string, extra: Partial<ViolationCause> = {}): ViolationCause => ({ description, ...extra });

describe("ruleTypeOf", () => {
    it("reads ruleViolation.ruleType from additionalData, where the generated SDK keeps fields it does not know yet", () => {
        expect(ruleTypeOf(violation({ additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } } }))).toBe("COMPATIBILITY");
    });

    it("is undefined when the server does not send ruleType", () => {
        expect(ruleTypeOf(violation({ nameEscaped: "RuleViolationException" }))).toBeUndefined();
    });
});

describe("causesOf", () => {
    it("prefers ruleViolation.causes over the deprecated top-level causes", () => {
        expect(causesOf(violation({
            causes: [{ description: "top", context: "/t" }],
            additionalData: { ruleViolation: { causes: [{ description: "nested", context: "/n" }] } }
        })).map(c => c.description)).toEqual(["nested"]);
    });

    it("falls back to the top-level causes when ruleViolation has none", () => {
        expect(causesOf(violation({ additionalData: { ruleViolation: { ruleType: "VALIDITY" } } })).map(c => c.description))
            .toEqual(["d"]);
    });

    it("is empty for an error with neither", () => {
        expect(causesOf({ nameEscaped: "NotFoundException" } as RuleViolationProblemDetails)).toEqual([]);
    });
});

describe("ruleTypeOf (top level)", () => {
    it("ignores a top-level ruleType, which the agreed contract does not have", () => {
        expect(ruleTypeOf(violation({ additionalData: { ruleType: "COMPATIBILITY" } }))).toBeUndefined();
    });
});

describe("isRuleViolation", () => {
    it("falls back to the exception name when ruleType is absent", () => {
        expect(isRuleViolation(violation({ nameEscaped: "RuleViolationException" }))).toBe(true);
    });

    it("uses ruleViolation when present, so a renamed exception still counts", () => {
        expect(isRuleViolation(violation({ nameEscaped: "SomethingElse", additionalData: { ruleViolation: { ruleType: "VALIDITY" } } })))
            .toBe(true);
    });

    it("recognises the 3.5 shape, with causes only under ruleViolation and no exception name", () => {
        expect(isRuleViolation({
            additionalData: { ruleViolation: { causes: [{ description: "d", context: "/c" }] } }
        } as RuleViolationProblemDetails)).toBe(true);
    });

    it("is false without causes, so the modal keeps showing the detail text", () => {
        expect(isRuleViolation({ nameEscaped: "RuleViolationException", causes: [] } as RuleViolationProblemDetails))
            .toBe(false);
    });

    it("is false for other errors", () => {
        expect(isRuleViolation(violation({ nameEscaped: "ArtifactNotFoundException" }))).toBe(false);
    });
});

describe("isCompatibilityViolation", () => {
    it("is true for ruleType COMPATIBILITY", () => {
        expect(isCompatibilityViolation(violation({ additionalData: { ruleViolation: { ruleType: "COMPATIBILITY" } } }))).toBe(true);
    });

    it("is false for ruleType VALIDITY", () => {
        expect(isCompatibilityViolation(violation({ additionalData: { ruleViolation: { ruleType: "VALIDITY" } } }))).toBe(false);
    });

    it("is true for any rule violation when ruleType is absent", () => {
        expect(isCompatibilityViolation(violation({ nameEscaped: "RuleViolationException" }))).toBe(true);
    });
});

describe("toViolationCause", () => {
    it("keeps description and context, and reads the new fields from additionalData", () => {
        expect(toViolationCause({
            description: "The 'maxLength' string-length limit was decreased.",
            context: "/properties/name/maxLength",
            additionalData: {
                code: "STRING_TYPE_MAX_LENGTH_DECREASED",
                direction: "BACKWARD",
                proposed: { pointer: "/properties/name/maxLength" },
                existing: { pointer: "/properties/name/maxLength", version: "3" }
            }
        })).toEqual({
            description: "The 'maxLength' string-length limit was decreased.",
            context: "/properties/name/maxLength",
            code: "STRING_TYPE_MAX_LENGTH_DECREASED",
            direction: "BACKWARD",
            proposed: { pointer: "/properties/name/maxLength", reference: undefined, version: undefined },
            existing: { pointer: "/properties/name/maxLength", reference: undefined, version: "3" }
        });
    });

    it("keeps the empty pointer, which is the whole document", () => {
        expect(toViolationCause({ description: "root", additionalData: { proposed: { pointer: "" } } }).proposed)
            .toEqual({ pointer: "", reference: undefined, version: undefined });
    });

    it("leaves the new fields undefined for a cause with only description and context", () => {
        expect(toViolationCause({ description: "b", context: "/fields/1" })).toEqual({
            description: "b",
            context: "/fields/1",
            code: undefined,
            direction: undefined,
            proposed: undefined,
            existing: undefined
        });
    });
});

describe("groupCausesByLocation", () => {
    it("groups by proposed.pointer, falling back to context, in the order first seen", () => {
        const groups = groupCausesByLocation([
            cause("a", { context: "/x", proposed: { pointer: "/p" } }),
            cause("b", { context: "/q" }),
            cause("c", { context: "/y", proposed: { pointer: "/p" } })
        ]);

        expect(groups.map(g => g.location)).toEqual(["/p", "/q"]);
        expect(groups[0].causes.map(c => c.description)).toEqual(["a", "c"]);
    });

    it("keeps a location in a referenced artifact apart from the same pointer in the document", () => {
        const groups = groupCausesByLocation([
            cause("in document", { proposed: { pointer: "/p" } }),
            cause("in reference", { proposed: { pointer: "/p", reference: "common.json" } })
        ]);

        expect(groups).toHaveLength(2);
        expect(groups[0].reference).toBeUndefined();
        expect(groups[1].reference).toBe("common.json");
    });

    it("gives each cause without a proposed location its own row, as the plain list does", () => {
        const groups = groupCausesByLocation([
            cause("first", { context: "/same" }),
            cause("second", { context: "/same" }),
            cause("no context")
        ]);

        expect(groups.map(g => [g.location, g.causes.map(c => c.description)])).toEqual([
            ["/same", ["first"]],
            ["/same", ["second"]],
            [undefined, ["no context"]]
        ]);
    });
});

describe("comparedVersionOf", () => {
    it("returns the first existing.version reported", () => {
        expect(comparedVersionOf([cause("a"), cause("b", { existing: { version: "3" } })])).toBe("3");
    });

    it("ignores a version on a location in a referenced artifact", () => {
        expect(comparedVersionOf([
            cause("a", { existing: { pointer: "/p", reference: "common.json", version: "7" } }),
            cause("b", { existing: { pointer: "/q", version: "3" } })
        ])).toBe("3");
    });

    it("is undefined when the only version is a referenced artifact's", () => {
        expect(comparedVersionOf([cause("a", { existing: { reference: "common.json", version: "7" } })])).toBeUndefined();
    });

    it("is undefined when no cause names the version", () => {
        expect(comparedVersionOf([cause("a", { context: "/x" })])).toBeUndefined();
    });
});

describe("toSubmittedContent", () => {
    it("turns null SDK fields into undefined", () => {
        expect(toSubmittedContent({ content: "{}", contentType: null })).toEqual({ content: "{}", contentType: undefined });
    });

    it("handles a missing body", () => {
        expect(toSubmittedContent(undefined)).toEqual({ content: undefined, contentType: undefined });
    });
});
