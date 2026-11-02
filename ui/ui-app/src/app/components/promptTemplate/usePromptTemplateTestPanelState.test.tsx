// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, renderHook } from "@testing-library/react";
import { RenderPromptResponse } from "@models/RenderPromptResponse.ts";
import { VariableSchema } from "./promptTemplateVariables";
import {
    UsePromptTemplateTestPanelStateArgs,
    usePromptTemplateTestPanelState
} from "./usePromptTemplateTestPanelState";

type Variables = Record<string, VariableSchema> | undefined;

type Deferred = {
    promise: Promise<RenderPromptResponse>;
    resolve: (v: RenderPromptResponse) => void;
};

const deferred = (): Deferred => {
    let resolve!: (v: RenderPromptResponse) => void;
    const promise = new Promise<RenderPromptResponse>((res) => {
        resolve = res;
    });
    return { promise, resolve };
};

const V1_TEMPLATE = "{{cfg}}";
const v1Variables = (): Variables => ({ cfg: { type: "object", default: { a: 1 } } });
const V2_TEMPLATE = "{{items}}";
const v2Variables = (): Variables => ({ items: { type: "array", default: [1, 2] } });

const args = (
    version: string,
    template: string | undefined,
    variables: Variables,
    renderPromptTemplate: UsePromptTemplateTestPanelStateArgs["groups"]["renderPromptTemplate"] = vi.fn()
): UsePromptTemplateTestPanelStateArgs => ({
    groupId: "g",
    artifactId: "a",
    version,
    template,
    variables,
    groups: { renderPromptTemplate }
});

afterEach(() => {
    cleanup();
});

describe("usePromptTemplateTestPanelState", () => {
    it("initializes values and raw text from content present at mount", () => {
        const { result } = renderHook(() => usePromptTemplateTestPanelState(args("1", V1_TEMPLATE, v1Variables())));

        expect(result.current.values).toEqual({ cfg: { a: 1 } });
        expect(result.current.rawTexts).toEqual({ cfg: "{\n  \"a\": 1\n}" });
    });

    it("initializes once content arrives after mount", () => {
        const { result, rerender } = renderHook(
            (props: UsePromptTemplateTestPanelStateArgs) => usePromptTemplateTestPanelState(props),
            { initialProps: args("1", undefined, undefined) }
        );
        expect(result.current.rawTexts).toEqual({});

        rerender(args("1", V1_TEMPLATE, v1Variables()));

        expect(result.current.values).toEqual({ cfg: { a: 1 } });
        expect(result.current.rawTexts).toEqual({ cfg: "{\n  \"a\": 1\n}" });
    });

    it("picks up the new version's content when it arrives after the version switch", () => {
        const { result, rerender } = renderHook(
            (props: UsePromptTemplateTestPanelStateArgs) => usePromptTemplateTestPanelState(props),
            { initialProps: args("1", V1_TEMPLATE, v1Variables()) }
        );

        // The version page keeps showing the previous content until the new content loads.
        rerender(args("2", V1_TEMPLATE, v1Variables()));
        rerender(args("2", V2_TEMPLATE, v2Variables()));

        expect(result.current.values).toEqual({ items: [1, 2] });
        expect(result.current.rawTexts).toEqual({ items: "[\n  1,\n  2\n]" });
    });

    it("keeps user input when the parent passes equal content in new object references", () => {
        const { result, rerender } = renderHook(
            (props: UsePromptTemplateTestPanelStateArgs) => usePromptTemplateTestPanelState(props),
            { initialProps: args("1", V1_TEMPLATE, v1Variables()) }
        );

        act(() => result.current.setObjectText("cfg", "{\"b\":2}"));
        rerender(args("1", V1_TEMPLATE, v1Variables()));

        expect(result.current.rawTexts.cfg).toBe("{\"b\":2}");
        expect(result.current.values.cfg).toEqual({ b: 2 });
    });

    it("stores raw text verbatim and keeps the parsed value in sync", () => {
        const { result } = renderHook(() => usePromptTemplateTestPanelState(args("1", V1_TEMPLATE, v1Variables())));

        act(() => result.current.setObjectText("cfg", "{\"a\":"));
        expect(result.current.rawTexts.cfg).toBe("{\"a\":");
        expect(result.current.values.cfg).toBe("{\"a\":");

        act(() => result.current.setObjectText("cfg", "{\"a\":1}"));
        expect(result.current.rawTexts.cfg).toBe("{\"a\":1}");
        expect(result.current.values.cfg).toEqual({ a: 1 });
    });

    it("submits the parsed object value on Render, not the raw text", async () => {
        const renderPromptTemplate = vi.fn().mockResolvedValue({ rendered: "out" });
        const { result } = renderHook(() =>
            usePromptTemplateTestPanelState(args("1", V1_TEMPLATE, v1Variables(), renderPromptTemplate)));

        act(() => result.current.setObjectText("cfg", "{\"a\":5}"));
        await act(async () => result.current.doRender());

        expect(renderPromptTemplate).toHaveBeenCalledTimes(1);
        expect(renderPromptTemplate).toHaveBeenCalledWith("g", "a", "1", { cfg: { a: 5 } });
        expect(result.current.renderedOutput).toBe("out");
    });

    it("ignores a Render response that resolves after the new version's content arrived", async () => {
        const pending = deferred();
        const renderPromptTemplate = vi.fn().mockReturnValue(pending.promise);
        const { result, rerender } = renderHook(
            (props: UsePromptTemplateTestPanelStateArgs) => usePromptTemplateTestPanelState(props),
            { initialProps: args("1", V1_TEMPLATE, v1Variables(), renderPromptTemplate) }
        );

        act(() => result.current.doRender());
        expect(result.current.isLoading).toBe(true);

        rerender(args("2", V2_TEMPLATE, v2Variables(), renderPromptTemplate));
        await act(async () => pending.resolve({ rendered: "stale" }));

        expect(result.current.renderedOutput).toBe("");
        expect(result.current.isLoading).toBe(false);
    });
});
