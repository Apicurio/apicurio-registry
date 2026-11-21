import { describe, expect, it } from "vitest";
import { isPermitted } from "./permissions.utils";

describe("isPermitted", () => {
    it("follows the reported flag", () => {
        const readOnly = { read: true, write: false, admin: false };
        expect(isPermitted(readOnly)).toBe(false);
        expect(isPermitted(readOnly, "write")).toBe(false);
        expect(isPermitted(readOnly, "read")).toBe(true);
        expect(isPermitted(readOnly, "admin")).toBe(false);
        expect(isPermitted({ read: true, write: true, admin: false })).toBe(true);
    });

    it("permits everything when no permissions are reported", () => {
        expect(isPermitted(undefined)).toBe(true);
        expect(isPermitted(null)).toBe(true);
        expect(isPermitted({})).toBe(true);
        expect(isPermitted({ read: true, write: null, admin: null }, "write")).toBe(true);
    });
});
