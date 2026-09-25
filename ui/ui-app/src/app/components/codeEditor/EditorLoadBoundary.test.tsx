// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { ComponentType, lazy } from "react";

describe("EditorLoadBoundary", () => {
    afterEach(() => {
        cleanup();
        vi.resetModules();
    });

    it("shows a pending status while children are suspended", async () => {
        const { EditorLoadBoundary } = await import("./EditorLoadBoundary");
        let resolveChild: (module: { default: ComponentType }) => void = () => {};
        const LazyChild = lazy(() => new Promise<{ default: ComponentType }>(resolve => {
            resolveChild = resolve;
        }));

        render(
            <EditorLoadBoundary height="300px" width="100%">
                <LazyChild />
            </EditorLoadBoundary>
        );

        expect(screen.getByRole("status")).toHaveTextContent("Loading code editor");

        await act(async () => {
            resolveChild({ default: () => <div data-testid="resolved-child">loaded</div> });
        });

        expect(screen.getByTestId("resolved-child")).toHaveTextContent("loaded");
    });

    it("shows slow-load guidance after 30 seconds while still pending", async () => {
        vi.useFakeTimers();
        const { EditorLoadBoundary } = await import("./EditorLoadBoundary");
        const LazyChild = lazy(() => new Promise<{ default: ComponentType }>(() => {}));

        render(
            <EditorLoadBoundary height="300px" width="100%">
                <LazyChild />
            </EditorLoadBoundary>
        );

        expect(screen.getByRole("status")).toHaveTextContent("Loading code editor");

        act(() => {
            vi.advanceTimersByTime(30_000);
        });

        expect(screen.getByRole("status")).toHaveTextContent("taking longer than expected");
        vi.useRealTimers();
    });

    it("renders a local error state when a lazily-loaded child's import rejects", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const { EditorLoadBoundary } = await import("./EditorLoadBoundary");
        const LazyChild = lazy(() => Promise.reject(new Error("chunk failed to load")));

        render(
            <EditorLoadBoundary height="300px" width="100%">
                <LazyChild />
            </EditorLoadBoundary>
        );

        await waitFor(() => {
            expect(screen.getByTestId("editor-load-boundary-error")).toHaveTextContent("Unable to load the code editor");
        });
    });

    it("renders a local error state, without a diagnostic fallback, when a child throws", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const { EditorLoadBoundary } = await import("./EditorLoadBoundary");

        function ThrowingChild(): null {
            throw new Error("boom");
        }

        render(
            <EditorLoadBoundary height="300px" width="100%">
                <ThrowingChild />
            </EditorLoadBoundary>
        );

        expect(screen.getByTestId("editor-load-boundary-error")).toHaveTextContent("Unable to load the code editor");
        expect(screen.queryByTestId("editor-load-boundary-diagnostic")).toBeNull();
    });

    it("includes escaped diagnostic fallback text on error when supplied", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const { EditorLoadBoundary } = await import("./EditorLoadBoundary");

        function ThrowingChild(): null {
            throw new Error("boom");
        }

        render(
            <EditorLoadBoundary height="300px" width="100%" diagnosticFallbackText="<script>evil()</script>">
                <ThrowingChild />
            </EditorLoadBoundary>
        );

        const diagnostic = screen.getByTestId("editor-load-boundary-diagnostic");
        expect(diagnostic).toHaveTextContent("<script>evil()</script>");
        expect(diagnostic.innerHTML).not.toContain("<script>evil()</script>");
    });

    it("reloads the page when the reload button is clicked after a failure", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const reload = vi.fn();
        Object.defineProperty(window, "location", {
            value: { reload },
            writable: true
        });
        const { EditorLoadBoundary } = await import("./EditorLoadBoundary");

        function ThrowingChild(): null {
            throw new Error("boom");
        }

        const { getByTestId } = render(
            <EditorLoadBoundary height="300px" width="100%">
                <ThrowingChild />
            </EditorLoadBoundary>
        );

        getByTestId("editor-load-boundary-reload-btn").click();
        expect(reload).toHaveBeenCalledTimes(1);
    });
});
