// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";

function mockRuntime(): { finish: (runtime: Record<string, unknown>) => void; loadMonacoRuntime: ReturnType<typeof vi.fn> } {
    let resolveRuntime: (runtime: Record<string, unknown>) => void = () => {};
    const runtimePromise = new Promise<Record<string, unknown>>(resolve => {
        resolveRuntime = resolve;
    });
    const loadMonacoRuntime = vi.fn().mockReturnValue(runtimePromise);
    vi.doMock("./loadMonacoRuntime", () => ({ loadMonacoRuntime }));
    return { finish: resolveRuntime, loadMonacoRuntime };
}

describe("RegistryEditors", () => {
    afterEach(() => {
        cleanup();
        vi.resetModules();
        vi.clearAllMocks();
    });

    it("does not mount the underlying editor before the runtime resolves", async () => {
        const { finish } = mockRuntime();
        const MockEditor = (props: { value?: string }) => <div data-testid="mock-editor">{props.value}</div>;

        const { RegistryCodeEditor } = await import("./RegistryEditors");

        render(<RegistryCodeEditor value="hello" />);

        expect(screen.getByRole("status")).toHaveTextContent("Loading code editor");
        expect(screen.queryByTestId("mock-editor")).toBeNull();

        await act(async () => {
            finish({ Editor: MockEditor, DiffEditor: MockEditor });
        });

        expect(screen.getByTestId("mock-editor")).toHaveTextContent("hello");
    });

    it("forwards props to the underlying diff editor once loaded", async () => {
        const { finish } = mockRuntime();
        const MockDiffEditor = (props: { original?: string; modified?: string }) =>
            <div data-testid="mock-diff-editor">{`${props.original}|${props.modified}`}</div>;

        const { RegistryDiffEditor } = await import("./RegistryEditors");

        render(<RegistryDiffEditor original="a" modified="b" />);

        await act(async () => {
            finish({ Editor: MockDiffEditor, DiffEditor: MockDiffEditor });
        });

        expect(screen.getByTestId("mock-diff-editor")).toHaveTextContent("a|b");
    });

    it("shares one runtime load across concurrently mounted editors", async () => {
        const { finish, loadMonacoRuntime } = mockRuntime();
        const MockEditor = (props: { value?: string }) => <div data-testid="mock-editor">{props.value}</div>;

        const { RegistryCodeEditor } = await import("./RegistryEditors");

        render(
            <div>
                <RegistryCodeEditor value="first" />
                <RegistryCodeEditor value="second" />
            </div>
        );

        await act(async () => {
            finish({ Editor: MockEditor, DiffEditor: MockEditor });
        });

        const editors = screen.getAllByTestId("mock-editor");
        expect(editors).toHaveLength(2);
        expect(editors[0]).toHaveTextContent("first");
        expect(editors[1]).toHaveTextContent("second");
        expect(loadMonacoRuntime).toHaveBeenCalledTimes(1);
    });

    it("mounts the PatternFly adapter behind the same runtime gate and forwards its language/code", async () => {
        const { finish } = mockRuntime();
        const PatternFlyEditorAdapter = (props: { code?: string; language?: string }) =>
            <div data-testid="mock-pf-editor">{`${props.language}:${props.code}`}</div>;
        vi.doMock("./PatternFlyEditorAdapter", () => ({ PatternFlyEditorAdapter }));

        const { RegistryPatternFlyCodeEditor } = await import("./RegistryEditors");

        render(<RegistryPatternFlyCodeEditor code="{}" language="json" />);

        expect(screen.getByRole("status")).toHaveTextContent("Loading code editor");
        expect(screen.queryByTestId("mock-pf-editor")).toBeNull();

        await act(async () => {
            finish({});
        });

        expect(screen.getByTestId("mock-pf-editor")).toHaveTextContent("json:{}");
    });

    it("shows the diagnostic fallback text for the PatternFly editor if loading fails", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const loadMonacoRuntime = vi.fn().mockRejectedValue(new Error("chunk failed"));
        vi.doMock("./loadMonacoRuntime", () => ({ loadMonacoRuntime }));
        vi.doMock("./PatternFlyEditorAdapter", () => ({ PatternFlyEditorAdapter: () => null }));

        const { RegistryPatternFlyCodeEditor } = await import("./RegistryEditors");

        render(<RegistryPatternFlyCodeEditor code="bad-content" language="json" />);

        await waitFor(() => {
            expect(screen.getByTestId("editor-load-boundary-error")).toBeInTheDocument();
        });
        expect(screen.getByTestId("editor-load-boundary-diagnostic")).toHaveTextContent("bad-content");
    });
});
