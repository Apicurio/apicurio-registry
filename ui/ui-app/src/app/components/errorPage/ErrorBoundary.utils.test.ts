import { describe, expect, it } from "vitest";
import { shouldResetOnLocationChange } from "./ErrorBoundary.utils";

describe("shouldResetOnLocationChange", () => {
    it("does not reset when the boundary is not in an error state", () => {
        expect(shouldResetOnLocationChange(false, "/dashboard", "/explore")).toBe(false);
    });

    it("resets when the location changes while in an error state", () => {
        expect(shouldResetOnLocationChange(true, "/dashboard", "/explore")).toBe(true);
    });

    it("does not reset when the location is unchanged", () => {
        expect(shouldResetOnLocationChange(true, "/explore", "/explore")).toBe(false);
    });

    it("does not reset when the current location is undefined", () => {
        expect(shouldResetOnLocationChange(true, "/explore", undefined)).toBe(false);
    });

    it("resets when the previous location was undefined and a location is now known", () => {
        expect(shouldResetOnLocationChange(true, undefined, "/explore")).toBe(true);
    });
});
