// This module is intentionally lightweight: it must never statically import the heavy
// Monaco runtime.  The dynamic import below is the only supported way for the rest of the
// application to reach it, so that navigating the app without opening a code editor never
// downloads or executes Monaco.

type MonacoRuntime = typeof import("./monacoRuntime");

let runtimePromise: Promise<MonacoRuntime> | undefined;

/**
 * Loads and initializes the shared, bundled Monaco runtime on first use.  Concurrent and
 * subsequent calls all resolve to the same promise/module instance; initialization runs
 * exactly once.  If initialization fails, the rejected promise is cached so every caller
 * observes the same failure (callers should surface a local error state rather than retry
 * automatically).
 */
export function loadMonacoRuntime(): Promise<MonacoRuntime> {
    if (!runtimePromise) {
        runtimePromise = import("./monacoRuntime").then(async (runtime: MonacoRuntime): Promise<MonacoRuntime> => {
            await runtime.initializeMonaco();
            return runtime;
        });
    }
    return runtimePromise;
}
