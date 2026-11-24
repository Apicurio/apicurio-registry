import { assertDashboardShape } from "./dashboard.js";
import { loadEnv } from "./env.js";

/** Runs once before any browser starts: the sealed input is validated and, when a Grafana is under test, it must answer. */
export default async function globalSetup(): Promise<void> {
    const ui = loadEnv();
    if (ui.mode === "selftest") {
        return;
    }
    assertDashboardShape();
    let status: number;
    try {
        const response = await fetch(`${ui.grafanaUrl}/api/health`, { signal: AbortSignal.timeout(10_000) });
        status = response.status;
    } catch (error) {
        throw new Error(`Grafana at ${ui.grafanaUrl} does not answer /api/health: ${(error as Error).message}. These tests do not start a stack.`);
    }
    if (status !== 200) {
        throw new Error(`Grafana at ${ui.grafanaUrl} answered HTTP ${status} on /api/health`);
    }
}
