import { join } from "node:path";

import { defineConfig } from "@playwright/test";

import { reporterOverrides } from "./lib/tooling.mjs";
import { EXPECT_TIMEOUT_MS, loadEnv } from "./support/env.js";

// Playwright's reporters read some environment variables before the configuration: a caller's value would send the report or
// the JSON file somewhere else, add a reporter or open a server. Run through ./test-ui.sh these are removed first; this refuses
// them for any other way of starting Playwright, before anything is written.
const overrides = reporterOverrides(process.env);
if (overrides.length > 0) {
    throw new Error(`these environment variables would redirect or change the reporters and are not accepted: ${overrides.join(", ")}. Unset them, or run ./test-ui.sh, which does.`);
}

const ui = loadEnv();

// One fixed look for every run: viewport, locale, time zone and colour scheme. Correctness is asserted on values, not on pixels.
export default defineConfig({
    testDir: "./specs",
    globalSetup: "./support/global-setup.ts",
    fullyParallel: false,
    workers: 1,
    retries: 0,
    forbidOnly: true,
    timeout: 90_000,
    expect: { timeout: EXPECT_TIMEOUT_MS },
    // Everything Playwright writes goes below this invocation's own directories; its output directory is emptied at the start, so
    // the files the runner and the tests keep (the sealed input, the screenshots) are beside it, not inside it.
    outputDir: join(ui.runDir, "pw"),
    reporter: [
        ["list"],
        ["html", { outputFolder: ui.reportDir, open: "never" }],
        ["json", { outputFile: join(ui.runDir, "results.json") }],
    ],
    use: {
        viewport: { width: 1500, height: 1500 },
        locale: "en-US",
        timezoneId: "UTC",
        colorScheme: "dark",
        reducedMotion: "reduce",
        screenshot: "only-on-failure",
        // Tracing is started explicitly after the login by the SafeTrace helper, in the no-network mode
        trace: "off",
        video: "off",
    },
    projects:
        ui.mode === "seeded"
            ? [
                  { name: "usage", testMatch: /(^|\/)(usage|variable-readiness)\.spec\.ts$/ },
                  { name: "screenshots", testMatch: /screenshots\.spec\.ts/ },
              ]
            : ui.mode === "scenario"
              ? [{ name: "process-switch", testMatch: /(process-switch|dependent-variable-readiness)\.spec\.ts/ }]
              : [{ name: "self-test", testMatch: /(trace-safety|route-matching|query-log)\.spec\.ts/ }],
});
