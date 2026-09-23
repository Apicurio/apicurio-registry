import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker.js?worker";
import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker.js?worker";
import CssWorker from "monaco-editor/esm/vs/language/css/css.worker.js?worker";
import HtmlWorker from "monaco-editor/esm/vs/language/html/html.worker.js?worker";
import TypeScriptWorker from "monaco-editor/esm/vs/language/typescript/ts.worker.js?worker";
import { registerCustomLanguages } from "@editors/registerLanguages.ts";

// Re-export the upstream React components.  Consumers must only ever obtain these through
// "loadMonacoRuntime.ts" (never by importing this module directly), so that Monaco's
// implementation is not pulled into the initial application bundle.
export { Editor, DiffEditor } from "@monaco-editor/react";

/**
 * Selects the Vite-built worker constructor for a given Monaco worker "label".  This mirrors
 * Monaco's own default worker mapping (see "monaco-editor/esm/vs/language/*\/workerManager.js"),
 * but sources the workers from our own bundle instead of the CDN.
 */
function chooseWorkerConstructor(label: string): new () => Worker {
    switch (label) {
        case "json":
            return JsonWorker;
        case "css":
        case "scss":
        case "less":
            return CssWorker;
        case "html":
        case "handlebars":
        case "razor":
            return HtmlWorker;
        case "typescript":
        case "javascript":
            return TypeScriptWorker;
        default:
            return EditorWorker;
    }
}

let initialized = false;

/**
 * Configures the bundled Monaco runtime: installs the worker factory, registers Registry's
 * custom languages (protobuf, graphql), and points the shared "@monaco-editor/react" loader at
 * our locally bundled Monaco instance instead of the jsDelivr CDN.
 *
 * Must be awaited exactly once, before any "Editor"/"DiffEditor" (or the PatternFly "CodeEditor",
 * which shares the same loader) is mounted.  Safe to call multiple times; only the first call has
 * an effect.
 */
export async function initializeMonaco(): Promise<void> {
    if (initialized) {
        return;
    }
    initialized = true;

    // Monaco reads its worker factory from "globalThis.MonacoEnvironment" (not "self"), so this
    // works identically in the browser and under Node-based unit tests.
    (globalThis as any).MonacoEnvironment = {
        ...(globalThis as any).MonacoEnvironment,
        getWorker(_workerId: string, label: string): Worker {
            const WorkerConstructor: new () => Worker = chooseWorkerConstructor(label);
            return new WorkerConstructor();
        }
    };

    registerCustomLanguages(monaco);

    loader.config({ monaco });
    await loader.init();
}
