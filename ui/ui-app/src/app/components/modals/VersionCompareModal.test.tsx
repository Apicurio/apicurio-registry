import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * These tests drive the component's load effect directly rather than rendering it, which is the
 * same approach used by useThemeService.test.tsx. The vitest environment is "node", so there is no
 * DOM available to render into.
 *
 * The behaviour under test is that a load belonging to a superseded version pair can no longer
 * write state, which is what newLoaderGuard() provides. See #8880 for the original fix that
 * introduced the guard for the page loaders.
 */

const setters: Record<string, any> = {};
let effects: Array<{ fn: () => any; deps: any[] }> = [];
let stateIndex = 0;
const stateNames = ["version1Content", "version2Content", "isLoading", "error"];

vi.mock("react", async (importOriginal) => {
    const actual = await importOriginal<typeof import("react")>();
    return {
        ...actual,
        useState: vi.fn((init: any) => {
            const name = stateNames[stateIndex] ?? `extra${stateIndex}`;
            stateIndex++;
            if (!setters[name]) {
                setters[name] = vi.fn();
            }
            return [typeof init === "function" ? init() : init, setters[name]];
        }),
        useEffect: vi.fn((fn: any, deps: any[]) => {
            effects.push({ fn, deps });
        })
    };
});

// The vitest environment is "node", so PatternFly's CSS side effects cannot load. Only the load
// effect is under test here, so the presentational imports are stubbed out.
vi.mock("@patternfly/react-core", () => ({ Spinner: () => null, Alert: () => null }));
vi.mock("@patternfly/react-core/deprecated", () => ({ Modal: () => null }));
vi.mock("@app/components", () => ({ DiffView: () => null }));
vi.mock("./VersionCompareModal.css", () => ({}));

const getArtifactVersionContent = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({ getArtifactVersionContent })
}));

const deferred = () => {
    let resolve!: (v: string) => void;
    let reject!: (e: unknown) => void;
    const promise = new Promise<string>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
};

const version = (name: string, createdOn: string) => ({ version: name, createdOn } as any);

/** Renders the component function once and returns the load effect that was registered. */
const runComponent = async (version1: any, version2: any) => {
    const { VersionCompareModal } = await import("./VersionCompareModal");
    stateIndex = 0;
    effects = [];
    (VersionCompareModal as any)({
        isOpen: true,
        groupId: "g",
        artifactId: "a",
        version1,
        version2,
        onClose: vi.fn()
    });
    // The first effect is the loader; the second resets state when the modal closes.
    return effects[0];
};

describe("VersionCompareModal load guard", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        for (const key of Object.keys(setters)) {
            delete setters[key];
        }
        stateIndex = 0;
        effects = [];
    });

    it("ignores a resolution belonging to a superseded version pair", async () => {
        const a1 = deferred();
        const a2 = deferred();
        getArtifactVersionContent.mockReturnValueOnce(a1.promise).mockReturnValueOnce(a2.promise);

        const first = await runComponent(version("1", "2026-01-01"), version("2", "2026-01-02"));
        const cleanup = first.fn();

        // The user picks a different pair, so React cleans up the previous effect run.
        cleanup?.();

        a1.resolve("stale-content-1");
        a2.resolve("stale-content-2");
        await Promise.resolve();
        await Promise.resolve();

        expect(setters.version1Content).not.toHaveBeenCalled();
        expect(setters.version2Content).not.toHaveBeenCalled();
    });

    it("does not clear the loading state from a superseded run", async () => {
        const a1 = deferred();
        const a2 = deferred();
        getArtifactVersionContent.mockReturnValueOnce(a1.promise).mockReturnValueOnce(a2.promise);

        const first = await runComponent(version("1", "2026-01-01"), version("2", "2026-01-02"));
        const cleanup = first.fn();
        setters.isLoading.mockClear();
        cleanup?.();

        a1.resolve("x");
        a2.resolve("y");
        await Promise.resolve();
        await Promise.resolve();

        expect(setters.isLoading).not.toHaveBeenCalled();
    });

    it("does not surface an error from a superseded run", async () => {
        const a1 = deferred();
        const a2 = deferred();
        getArtifactVersionContent.mockReturnValueOnce(a1.promise).mockReturnValueOnce(a2.promise);

        const first = await runComponent(version("1", "2026-01-01"), version("2", "2026-01-02"));
        const cleanup = first.fn();
        setters.error.mockClear();
        cleanup?.();

        a1.reject(new Error("boom"));
        a2.resolve("y");
        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();

        expect(setters.error).not.toHaveBeenCalledWith("Failed to load version content. Please try again.");
    });

    it("still applies content for the current run, oldest version on the left", async () => {
        const a1 = deferred();
        const a2 = deferred();
        getArtifactVersionContent.mockReturnValueOnce(a1.promise).mockReturnValueOnce(a2.promise);

        // version1 is the newer of the two, so the contents must be swapped.
        const current = await runComponent(version("2", "2026-02-01"), version("1", "2026-01-01"));
        current.fn();

        a1.resolve("newer-content");
        a2.resolve("older-content");
        await Promise.resolve();
        await Promise.resolve();

        expect(setters.version1Content).toHaveBeenCalledWith("older-content");
        expect(setters.version2Content).toHaveBeenCalledWith("newer-content");
        expect(setters.isLoading).toHaveBeenCalledWith(false);
    });
});
