import { tmpdir } from "node:os";
import { join } from "node:path";

import { defineConfig } from "@playwright/test";

import { reporterOverrides } from "../../lib/tooling.mjs";

// The same refusal as the main configuration: a variable that would add or redirect a reporter is not accepted.
const overrides = reporterOverrides(process.env);
if (overrides.length > 0) {
    throw new Error(`these environment variables would redirect or change the reporters and are not accepted: ${overrides.join(", ")}`);
}

// The DOM fixture of the rate panel check (rate-panel.spec.ts): static HTML in a local browser, no Grafana, no login, no network, no
// stack. Nothing is written beside the sources; the short expect timeout only bounds the negative cases, which must fail.
export default defineConfig({
    testDir: ".",
    testMatch: "rate-panel.spec.ts",
    fullyParallel: false,
    workers: 1,
    retries: 0,
    forbidOnly: true,
    timeout: 30_000,
    expect: { timeout: 1500 },
    outputDir: join(tmpdir(), `usage-telemetry-otel-rate-panel-${process.pid}`),
    reporter: "list",
    use: { viewport: { width: 1000, height: 800 }, locale: "en-US", trace: "off", video: "off", screenshot: "off" },
});
