import { defineConfig } from "@playwright/test";

// Used only by the readiness check of the runner: `playwright test --list` with this configuration loads the real command line, the
// test runner and its TypeScript loader, and runs no browser, reads no input and writes nothing.
export default defineConfig({ testDir: ".", testMatch: "readiness.spec.ts", reporter: "list" });
