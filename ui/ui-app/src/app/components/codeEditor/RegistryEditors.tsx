import { ComponentType, FunctionComponent, lazy, LazyExoticComponent } from "react";
import type { DiffEditorProps, EditorProps } from "@monaco-editor/react";
import { EditorLoadBoundary } from "./EditorLoadBoundary";
import { loadMonacoRuntime } from "./loadMonacoRuntime";
import type { RegistryPatternFlyCodeEditorProps } from "./PatternFlyEditorAdapter";

// These "lazy()" declarations must stay at module scope (not inside a component body), so that
// React caches a single component - and a single underlying import - per app lifetime, shared by
// every mounted editor.  None of them may be imported eagerly anywhere else in the application;
// this file is the only supported entry point into the bundled Monaco/PatternFly editors.

const LazyEditor: LazyExoticComponent<ComponentType<EditorProps>> = lazy(async () => {
    const runtime = await loadMonacoRuntime();
    return { default: runtime.Editor };
});

const LazyDiffEditor: LazyExoticComponent<ComponentType<DiffEditorProps>> = lazy(async () => {
    const runtime = await loadMonacoRuntime();
    return { default: runtime.DiffEditor };
});

const LazyPatternFlyEditor: LazyExoticComponent<ComponentType<RegistryPatternFlyCodeEditorProps>> = lazy(async () => {
    const [, adapter] = await Promise.all([
        loadMonacoRuntime(),
        import("./PatternFlyEditorAdapter")
    ]);
    return { default: adapter.PatternFlyEditorAdapter };
});

/**
 * Drop-in, lazily-loaded replacement for "@monaco-editor/react"'s "Editor".  Defers loading the
 * bundled Monaco runtime until first mounted, and shows a local loading/error state instead of
 * fetching any editor assets from an external CDN.
 */
export const RegistryCodeEditor: FunctionComponent<EditorProps> = (props: EditorProps) => {
    return (
        <EditorLoadBoundary height={props.height} width={props.width}>
            <LazyEditor {...props} />
        </EditorLoadBoundary>
    );
};

/**
 * Drop-in, lazily-loaded replacement for "@monaco-editor/react"'s "DiffEditor".
 */
export const RegistryDiffEditor: FunctionComponent<DiffEditorProps> = (props: DiffEditorProps) => {
    return (
        <EditorLoadBoundary height={props.height} width={props.width}>
            <LazyDiffEditor {...props} />
        </EditorLoadBoundary>
    );
};

/**
 * Drop-in, lazily-loaded replacement for "@patternfly/react-code-editor"'s "CodeEditor".  Shares
 * the same Monaco runtime and loading gate as "RegistryCodeEditor"/"RegistryDiffEditor".
 */
export const RegistryPatternFlyCodeEditor: FunctionComponent<RegistryPatternFlyCodeEditorProps> = (
    props: RegistryPatternFlyCodeEditorProps
) => {
    return (
        <EditorLoadBoundary height={props.height} width={props.width} diagnosticFallbackText={props.code}>
            <LazyPatternFlyEditor {...props} />
        </EditorLoadBoundary>
    );
};

export type { RegistryPatternFlyCodeEditorProps };
