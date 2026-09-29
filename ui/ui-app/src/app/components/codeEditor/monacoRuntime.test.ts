import { afterEach, describe, expect, it, vi } from "vitest";

const MONACO_MARKER: unique symbol = Symbol("monaco-instance");

class JsonWorkerMock {
    public label = "json";
}
class CssWorkerMock {
    public label = "css";
}
class HtmlWorkerMock {
    public label = "html";
}
class TypeScriptWorkerMock {
    public label = "typescript";
}
class EditorWorkerMock {
    public label = "editor";
}

function mockModules(): { loaderConfig: ReturnType<typeof vi.fn>; loaderInit: ReturnType<typeof vi.fn>; registerCustomLanguages: ReturnType<typeof vi.fn> } {
    const loaderConfig = vi.fn();
    const loaderInit = vi.fn().mockResolvedValue(undefined);
    const registerCustomLanguages = vi.fn();

    vi.doMock("@monaco-editor/react", () => ({
        loader: { config: loaderConfig, init: loaderInit }
    }));
    vi.doMock("monaco-editor", () => ({ __monacoMarker: MONACO_MARKER }));
    vi.doMock("monaco-editor/esm/vs/editor/editor.worker.js?worker", () => ({ default: EditorWorkerMock }));
    vi.doMock("monaco-editor/esm/vs/language/json/json.worker.js?worker", () => ({ default: JsonWorkerMock }));
    vi.doMock("monaco-editor/esm/vs/language/css/css.worker.js?worker", () => ({ default: CssWorkerMock }));
    vi.doMock("monaco-editor/esm/vs/language/html/html.worker.js?worker", () => ({ default: HtmlWorkerMock }));
    vi.doMock("monaco-editor/esm/vs/language/typescript/ts.worker.js?worker", () => ({ default: TypeScriptWorkerMock }));
    vi.doMock("@editors/registerLanguages.ts", () => ({ registerCustomLanguages }));

    return { loaderConfig, loaderInit, registerCustomLanguages };
}

describe("initializeMonaco", () => {
    afterEach(() => {
        vi.resetModules();
        vi.clearAllMocks();
        delete (globalThis as any).MonacoEnvironment;
    });

    it("registers custom languages and configures the loader with the bundled monaco instance", async () => {
        const { loaderConfig, loaderInit, registerCustomLanguages } = mockModules();

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();

        expect(registerCustomLanguages).toHaveBeenCalledTimes(1);
        expect(registerCustomLanguages).toHaveBeenCalledWith({ __monacoMarker: MONACO_MARKER });
        expect(loaderConfig).toHaveBeenCalledTimes(1);
        expect(loaderConfig).toHaveBeenCalledWith({ monaco: { __monacoMarker: MONACO_MARKER } });
        expect(loaderInit).toHaveBeenCalledTimes(1);
    });

    it("registers custom languages and configures the loader before init resolves", async () => {
        const { loaderConfig, registerCustomLanguages } = mockModules();

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();

        const registerOrder: number = registerCustomLanguages.mock.invocationCallOrder[0];
        const configOrder: number = loaderConfig.mock.invocationCallOrder[0];
        expect(registerOrder).toBeLessThan(configOrder);
    });

    it("initializes only once when called multiple times", async () => {
        const { loaderConfig, loaderInit, registerCustomLanguages } = mockModules();

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();
        await initializeMonaco();
        await initializeMonaco();

        expect(registerCustomLanguages).toHaveBeenCalledTimes(1);
        expect(loaderConfig).toHaveBeenCalledTimes(1);
        expect(loaderInit).toHaveBeenCalledTimes(1);
    });

    it("does not construct any worker while initializing", async () => {
        mockModules();

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();

        expect((globalThis as any).MonacoEnvironment).toBeDefined();
        expect(typeof (globalThis as any).MonacoEnvironment.getWorker).toBe("function");
    });

    it.each([
        ["json", JsonWorkerMock],
        ["css", CssWorkerMock],
        ["scss", CssWorkerMock],
        ["less", CssWorkerMock],
        ["html", HtmlWorkerMock],
        ["handlebars", HtmlWorkerMock],
        ["razor", HtmlWorkerMock],
        ["typescript", TypeScriptWorkerMock],
        ["javascript", TypeScriptWorkerMock],
        ["protobuf", EditorWorkerMock],
        ["graphql", EditorWorkerMock],
        ["some-unmapped-label", EditorWorkerMock],
    ])("maps worker label %s to the expected worker implementation", async (label, expectedCtor) => {
        mockModules();

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();

        const worker: any = (globalThis as any).MonacoEnvironment.getWorker("workerMain.js", label);
        expect(worker).toBeInstanceOf(expectedCtor);
    });

    it("returns a distinct worker instance on each call for the same label", async () => {
        mockModules();

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();

        const first: any = (globalThis as any).MonacoEnvironment.getWorker("workerMain.js", "json");
        const second: any = (globalThis as any).MonacoEnvironment.getWorker("workerMain.js", "json");
        expect(first).not.toBe(second);
    });

    it("preserves any pre-existing MonacoEnvironment fields", async () => {
        mockModules();
        (globalThis as any).MonacoEnvironment = { createTrustedTypesPolicy: () => undefined };

        const { initializeMonaco } = await import("./monacoRuntime");
        await initializeMonaco();

        expect(typeof (globalThis as any).MonacoEnvironment.createTrustedTypesPolicy).toBe("function");
        expect(typeof (globalThis as any).MonacoEnvironment.getWorker).toBe("function");
    });
});
