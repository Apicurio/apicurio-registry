// @vitest-environment jsdom
import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
    usePromptTemplateTestPanelState,
    UsePromptTemplateTestPanelStateArgs
} from "./usePromptTemplateTestPanelState";
import { RenderPromptResponse } from "@models/RenderPromptResponse.ts";

type Deferred<T> = {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason?: unknown) => void;
};

const deferred = <T,>(): Deferred<T> => {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
};

const baseArgs = (
    overrides: Partial<UsePromptTemplateTestPanelStateArgs> & {
        groups: UsePromptTemplateTestPanelStateArgs["groups"];
    }
): UsePromptTemplateTestPanelStateArgs => ({
    groupId: "default",
    artifactId: "greeter",
    version: "1",
    template: "Hello {{name}}",
    variables: {
        name: { type: "string", required: true, default: "Ada" }
    },
    ...overrides
});

describe("usePromptTemplateTestPanelState", () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it("debounces auto-render: fires after 500ms of a user edit, not before", async () => {
        const renderPromptTemplate = vi.fn().mockResolvedValue({ rendered: "Hello Ada" } satisfies RenderPromptResponse);
        const { result } = renderHook(() => usePromptTemplateTestPanelState(baseArgs({ groups: { renderPromptTemplate } })));

        await act(async () => {
            result.current.setValue("name", "Ada");
        });

        await act(async () => {
            vi.advanceTimersByTime(499);
        });
        expect(renderPromptTemplate).not.toHaveBeenCalled();

        await act(async () => {
            vi.advanceTimersByTime(1);
        });
        expect(renderPromptTemplate).toHaveBeenCalledTimes(1);
    });

    it("blocks auto-render via the required-fields gate until the required field is filled", async () => {
        const renderPromptTemplate = vi.fn().mockResolvedValue({ rendered: "ok" } satisfies RenderPromptResponse);
        const { result } = renderHook(() => usePromptTemplateTestPanelState(baseArgs({
            template: "Hello {{name}} {{title}}",
            variables: {
                name: { type: "string", required: true },
                title: { type: "string", required: false }
            },
            groups: { renderPromptTemplate }
        })));

        await act(async () => {
            result.current.setValue("title", "Dr");
        });
        await act(async () => {
            vi.advanceTimersByTime(500);
        });
        expect(renderPromptTemplate).not.toHaveBeenCalled();

        await act(async () => {
            result.current.setValue("name", "Ada");
        });
        await act(async () => {
            vi.advanceTimersByTime(500);
        });
        expect(renderPromptTemplate).toHaveBeenCalledTimes(1);
    });

    it("does not auto-render on mount when defaults already satisfy the gate and the user has not edited", async () => {
        const renderPromptTemplate = vi.fn().mockResolvedValue({ rendered: "Hello Ada" } satisfies RenderPromptResponse);
        renderHook(() => usePromptTemplateTestPanelState(baseArgs({ groups: { renderPromptTemplate } })));

        await act(async () => {
            vi.advanceTimersByTime(2000);
        });
        expect(renderPromptTemplate).not.toHaveBeenCalled();
    });

    it("aborts the previous in-flight request when a newer render supersedes it", async () => {
        const first = deferred<RenderPromptResponse>();
        const second = deferred<RenderPromptResponse>();
        const renderPromptTemplate = vi.fn()
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise);

        const { result } = renderHook(() => usePromptTemplateTestPanelState(baseArgs({ groups: { renderPromptTemplate } })));

        await act(async () => {
            result.current.doRender();
        });
        expect(renderPromptTemplate).toHaveBeenCalledTimes(1);
        const firstSignal = renderPromptTemplate.mock.calls[0][4] as AbortSignal;
        expect(result.current.isLoading).toBe(true);

        await act(async () => {
            result.current.doRender();
        });
        expect(renderPromptTemplate).toHaveBeenCalledTimes(2);
        expect(firstSignal.aborted).toBe(true);
        expect(result.current.isLoading).toBe(true);

        await act(async () => {
            first.resolve({ rendered: "stale" });
            await first.promise.catch(() => undefined);
        });
        expect(result.current.renderedOutput).toBe("");
        expect(result.current.isLoading).toBe(true);

        await act(async () => {
            second.resolve({ rendered: "fresh" });
            await second.promise;
        });
        expect(result.current.renderedOutput).toBe("fresh");
        expect(result.current.isLoading).toBe(false);
    });

    it("aborts the in-flight request on unmount", async () => {
        const pending = deferred<RenderPromptResponse>();
        const renderPromptTemplate = vi.fn().mockReturnValue(pending.promise);
        const { result, unmount } = renderHook(() => usePromptTemplateTestPanelState(baseArgs({ groups: { renderPromptTemplate } })));

        await act(async () => {
            result.current.doRender();
        });
        const signal = renderPromptTemplate.mock.calls[0][4] as AbortSignal;
        expect(signal.aborted).toBe(false);

        unmount();
        expect(signal.aborted).toBe(true);
    });

    it("aborts an in-flight request on version switch and ignores the stale resolution", async () => {
        const pending = deferred<RenderPromptResponse>();
        const renderPromptTemplate = vi.fn().mockReturnValue(pending.promise);

        const { result, rerender } = renderHook(
            (props: UsePromptTemplateTestPanelStateArgs) => usePromptTemplateTestPanelState(props),
            { initialProps: baseArgs({ groups: { renderPromptTemplate } }) }
        );

        await act(async () => {
            result.current.doRender();
        });
        expect(renderPromptTemplate).toHaveBeenCalledTimes(1);
        const firstSignal = renderPromptTemplate.mock.calls[0][4] as AbortSignal;
        expect(result.current.isLoading).toBe(true);

        await act(async () => {
            rerender(baseArgs({
                version: "2",
                template: "Hello {{name}}",
                variables: {
                    name: { type: "string", required: true, default: "Bob" }
                },
                groups: { renderPromptTemplate }
            }));
        });

        expect(firstSignal.aborted).toBe(true);
        expect(result.current.isLoading).toBe(false);
        expect(result.current.renderedOutput).toBe("");

        await act(async () => {
            pending.resolve({ rendered: "stale from v1" });
            await pending.promise.catch(() => undefined);
        });
        expect(result.current.renderedOutput).toBe("");
        expect(result.current.isLoading).toBe(false);

        // Dirty flag reset on version switch — debounce from setValues must not auto-render.
        await act(async () => {
            vi.advanceTimersByTime(2000);
        });
        expect(renderPromptTemplate).toHaveBeenCalledTimes(1);
    });
});
