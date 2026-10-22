import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

describe("loadMonacoRuntime", () => {
    beforeEach(() => {
        vi.resetModules();
    });

    afterEach(() => {
        vi.doUnmock("./monacoRuntime");
        vi.resetModules();
        vi.clearAllMocks();
    });

    it("initializes the runtime exactly once across concurrent calls", async () => {
        const initializeMonaco = vi.fn().mockResolvedValue(undefined);
        vi.doMock("./monacoRuntime", () => ({ initializeMonaco }));

        const { loadMonacoRuntime } = await import("./loadMonacoRuntime");

        const first = loadMonacoRuntime();
        const second = loadMonacoRuntime();

        expect(first).toBe(second);
        await first;
        await second;

        expect(initializeMonaco).toHaveBeenCalledTimes(1);
    });

    it("does not resolve until initialization completes", async () => {
        let finishInitialization: () => void = () => {};
        const initializeMonaco = vi.fn().mockReturnValue(new Promise<void>(resolve => {
            finishInitialization = resolve;
        }));
        vi.doMock("./monacoRuntime", () => ({ initializeMonaco }));

        const { loadMonacoRuntime } = await import("./loadMonacoRuntime");

        let resolved = false;
        const promise = loadMonacoRuntime().then(() => {
            resolved = true;
        });

        // Allow any already-queued microtasks to flush before asserting nothing has resolved.
        await Promise.resolve();
        expect(resolved).toBe(false);

        finishInitialization();
        await promise;

        expect(resolved).toBe(true);
    });

    it("resolves to the underlying runtime module", async () => {
        const initializeMonaco = vi.fn().mockResolvedValue(undefined);
        vi.doMock("./monacoRuntime", () => ({ initializeMonaco, Editor: "editor-marker" }));

        const { loadMonacoRuntime } = await import("./loadMonacoRuntime");

        const resolved: any = await loadMonacoRuntime();
        expect(resolved.Editor).toBe("editor-marker");
        expect(resolved.initializeMonaco).toBe(initializeMonaco);
    });

    it("caches a rejected initialization so every caller observes the same failure", async () => {
        const initializationError = new Error("worker setup failed");
        const initializeMonaco = vi.fn().mockRejectedValue(initializationError);
        vi.doMock("./monacoRuntime", () => ({ initializeMonaco }));

        const { loadMonacoRuntime } = await import("./loadMonacoRuntime");

        const first = loadMonacoRuntime();
        const second = loadMonacoRuntime();

        expect(first).toBe(second);
        await expect(first).rejects.toBe(initializationError);
        await expect(second).rejects.toBe(initializationError);
        expect(initializeMonaco).toHaveBeenCalledTimes(1);
    });
});
