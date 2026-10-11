import { describe, expect, it } from "vitest";
import { shouldOfferNavigateHome, shouldResetOnLocationChange } from "./ErrorBoundary.utils";

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

describe("shouldOfferNavigateHome", () => {
    it("offers the action from a route other than home", () => {
        expect(shouldOfferNavigateHome("/explore", "/dashboard")).toBe(true);
    });

    it("does not offer the action when the home route is the one that crashed", () => {
        expect(shouldOfferNavigateHome("/dashboard", "/dashboard")).toBe(false);
    });

    it("ignores a trailing slash on the current location", () => {
        expect(shouldOfferNavigateHome("/dashboard/", "/dashboard")).toBe(false);
    });

    it("ignores a trailing slash on the home location", () => {
        expect(shouldOfferNavigateHome("/dashboard", "/dashboard/")).toBe(false);
    });

    it("compares against a navigation prefixed home route", () => {
        expect(shouldOfferNavigateHome("/registry/dashboard", "/registry/dashboard")).toBe(false);
        expect(shouldOfferNavigateHome("/registry/explore", "/registry/dashboard")).toBe(true);
    });

    it("treats the root path as a route other than home", () => {
        expect(shouldOfferNavigateHome("/", "/dashboard")).toBe(true);
    });
});
