import { describe, expect, it, vi } from "vitest";
import { newLoaderGuard } from "./loader.utils";

describe("newLoaderGuard", () => {
    it("invokes a wrapped callback while the guard is active", () => {
        const guard = newLoaderGuard();
        const spy = vi.fn();

        guard.wrap(spy)("value");

        expect(spy).toHaveBeenCalledTimes(1);
        expect(spy).toHaveBeenCalledWith("value");
    });

    it("does not invoke a previously wrapped callback after cancel", () => {
        const guard = newLoaderGuard();
        const spy = vi.fn();
        const wrapped = guard.wrap(spy);

        guard.cancel();
        wrapped("value");

        expect(spy).not.toHaveBeenCalled();
    });

    it("does not invoke a callback wrapped after cancel", () => {
        const guard = newLoaderGuard();
        guard.cancel();
        const spy = vi.fn();

        guard.wrap(spy)("value");

        expect(spy).not.toHaveBeenCalled();
    });

    it("keeps separate guards independent", () => {
        const first = newLoaderGuard();
        const second = newLoaderGuard();
        const firstSpy = vi.fn();
        const secondSpy = vi.fn();

        first.cancel();
        first.wrap(firstSpy)("a");
        second.wrap(secondSpy)("b");

        expect(firstSpy).not.toHaveBeenCalled();
        expect(secondSpy).toHaveBeenCalledTimes(1);
        expect(secondSpy).toHaveBeenCalledWith("b");
    });
});
