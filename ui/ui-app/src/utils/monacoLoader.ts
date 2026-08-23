import { loader } from "@monaco-editor/react";

let configured = false;

/**
 * Points @monaco-editor/react at self-hosted Monaco assets instead of the CDN.
 * Must run once before any Editor/DiffEditor mounts.
 */
export function configureMonacoLoader(vsPath: string): void {
    if (configured) {
        return;
    }
    configured = true;
    loader.config({ paths: { vs: vsPath } });
}
