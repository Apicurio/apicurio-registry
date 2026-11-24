import { readFileSync } from "node:fs";

import { parseBundle, sha256Hex } from "../lib/input.mjs";
import type { ProcessScenario, RunState } from "./state.js";

/** The wait of every web-first assertion and of the query-completion wait; the Playwright configuration uses the same value. */
export const EXPECT_TIMEOUT_MS = 20_000;

export interface UiEnv {
    mode: "seeded" | "scenario" | "selftest";
    /** Absolute directory this invocation owns: the sealed bundle, screenshots and the Playwright output below it. */
    runDir: string;
    reportDir: string;
    /** Empty in self-test mode, which needs no Grafana. */
    grafanaUrl: string;
    datasourceUid: string;
    grafanaUser: string;
    grafanaPassword: string;
    state?: RunState;
    scenario?: ProcessScenario;
}

const required = (name: string): string => {
    const value = process.env[name];
    if (value === undefined || value === "") {
        throw new Error(
            `${name} is not set. Run the tests through ./test-ui.sh or ./screenshots.sh (or npm test, which calls them): the runner validates ` +
                "the input once, seals it, creates this invocation's own directories and sets exactly UI_BUNDLE and UI_BUNDLE_SHA256.",
        );
    }
    return value;
};

/**
 * Everything the tests need comes from one sealed bundle: the bytes must be the ones the runner hashed, and they are validated
 * again by the same parser that validated the input. The Grafana address and the datasource are not read from the environment.
 */
export function loadEnv(): UiEnv {
    const bytes = readFileSync(required("UI_BUNDLE"));
    const expected = required("UI_BUNDLE_SHA256");
    const actual = sha256Hex(bytes);
    if (actual !== expected) {
        throw new Error(`the sealed input changed after the runner recorded it (sha256 ${actual}, expected ${expected})`);
    }
    const bundle = parseBundle(bytes.toString("utf8"), true);
    return {
        mode: bundle.mode,
        runDir: bundle.runDir as string,
        reportDir: bundle.reportDir as string,
        grafanaUrl: bundle.grafanaUrl ?? "",
        datasourceUid: bundle.datasourceUid ?? "",
        grafanaUser: "admin",
        grafanaPassword: process.env["GRAFANA_ADMIN_PASSWORD"] ?? "admin",
        ...(bundle.state !== undefined ? { state: bundle.state } : {}),
        ...(bundle.scenario !== undefined ? { scenario: bundle.scenario } : {}),
    };
}
