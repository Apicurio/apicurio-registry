// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { SearchedVersion } from "@sdk/lib/generated-client/models";
import { VersionCompareModal } from "./VersionCompareModal";

const getArtifactVersionContent = vi.fn();
vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({ getArtifactVersionContent })
}));

// The real DiffView mounts a Monaco editor; a plain stand-in keeps the assertions about content.
vi.mock("@app/components", () => ({
    DiffView: (props: { original: string; modified: string }) => (
        <pre data-testid="diff">{`${props.original}|${props.modified}`}</pre>
    )
}));

type Deferred = { promise: Promise<string>; resolve: (v: string) => void; reject: (e: unknown) => void };

const deferred = (): Deferred => {
    let resolve!: (v: string) => void;
    let reject!: (e: unknown) => void;
    const promise = new Promise<string>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
};

const version = (name: string, createdOn: string): SearchedVersion =>
    ({ version: name, createdOn: new Date(createdOn) }) as SearchedVersion;

const v1 = version("1", "2026-01-01");
const v2 = version("2", "2026-01-02");
const v3 = version("3", "2026-01-03");

const modal = (version1: SearchedVersion, version2: SearchedVersion) => (
    <VersionCompareModal
        isOpen={true}
        groupId="g"
        artifactId="a"
        version1={version1}
        version2={version2}
        onClose={() => {}}
    />
);

/** Lets pending promise callbacks run and React apply the resulting state updates. */
const flush = async (): Promise<void> => {
    await act(async () => {
        await new Promise((r) => setTimeout(r, 0));
    });
};

/**
 * Renders the modal for the pair (v1, v2), then switches it to (v1, v3) while the first load is
 * still in flight. Returns the pending responses for both loads.
 */
const renderThenSwitchPair = async (): Promise<{ stale: Deferred[]; current: Deferred[] }> => {
    const stale = [deferred(), deferred()];
    const current = [deferred(), deferred()];
    getArtifactVersionContent
        .mockReturnValueOnce(stale[0].promise)
        .mockReturnValueOnce(stale[1].promise)
        .mockReturnValueOnce(current[0].promise)
        .mockReturnValueOnce(current[1].promise);

    const { rerender } = render(modal(v1, v2));
    rerender(modal(v1, v3));
    await flush();
    return { stale, current };
};

describe("VersionCompareModal", () => {
    afterEach(() => {
        cleanup();
        vi.clearAllMocks();
    });

    it("shows the older version on the left", async () => {
        getArtifactVersionContent.mockResolvedValueOnce("newer").mockResolvedValueOnce("older");

        render(modal(v2, v1));
        await flush();

        expect(screen.getByTestId("diff")).toHaveTextContent("older|newer");
    });

    it("ignores content that arrives late for a superseded version pair", async () => {
        const { stale, current } = await renderThenSwitchPair();

        current[0].resolve("current-1");
        current[1].resolve("current-3");
        await flush();
        stale[0].resolve("stale-1");
        stale[1].resolve("stale-2");
        await flush();

        expect(screen.getByTestId("diff")).toHaveTextContent("current-1|current-3");
    });

    it("keeps the spinner while the current pair is still loading", async () => {
        const { stale } = await renderThenSwitchPair();

        stale[0].resolve("stale-1");
        stale[1].resolve("stale-2");
        await flush();

        expect(screen.getByText("Loading version content...")).toBeInTheDocument();
        expect(screen.queryByTestId("diff")).not.toBeInTheDocument();
    });

    it("does not show an error from a superseded version pair", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const { stale, current } = await renderThenSwitchPair();

        current[0].resolve("current-1");
        current[1].resolve("current-3");
        await flush();
        stale[0].reject(new Error("boom"));
        await flush();

        expect(screen.queryByText("Failed to load version content. Please try again.")).not.toBeInTheDocument();
        expect(screen.getByTestId("diff")).toHaveTextContent("current-1|current-3");
    });
});
