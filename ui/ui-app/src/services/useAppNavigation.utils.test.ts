import { describe, expect, it } from "vitest";
import { effectiveNavPrefixPath } from "./useAppNavigation.utils.ts";

describe("effectiveNavPrefixPath", () => {

    it("does not double-prefix on exact match", () => {
        expect(effectiveNavPrefixPath("/registry", "/registry")).toBe("");
    });

    it("does not double-prefix on trailing-slash mismatch", () => {
        expect(effectiveNavPrefixPath("/registry/", "/registry")).toBe("");
        expect(effectiveNavPrefixPath("/registry", "/registry/")).toBe("");
    });

    it("only returns the non-overlapping remainder on subpath overlap", () => {
        expect(effectiveNavPrefixPath("/registry/ui", "/registry")).toBe("/ui");
    });

    it("returns navPrefixPath unchanged when contextPath is the default root", () => {
        expect(effectiveNavPrefixPath("/registry", "/")).toBe("/registry");
    });

    it("returns an empty string when navPrefixPath is unset", () => {
        expect(effectiveNavPrefixPath("", "/registry")).toBe("");
    });

});
