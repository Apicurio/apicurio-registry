import { expect, test as base, type Page } from "@playwright/test";

import { loadEnv, type UiEnv } from "./env.js";
import { SafeTrace } from "./trace.js";

/**
 * Signs in through the Grafana login form: the user name and password are typed into the fields and the button is pressed. The
 * default demo account makes Grafana offer a new password; that offer is skipped, never accepted, so the password is unchanged.
 * The tests store no browser state: no storage state file, no cookie jar; the context lives and dies with the test.
 */
export async function signIn(page: Page, ui: UiEnv): Promise<void> {
    await page.goto(`${ui.grafanaUrl}/login`);
    await page.getByLabel("Email or username").fill(ui.grafanaUser);
    await page.getByLabel("Password", { exact: true }).fill(ui.grafanaPassword);
    await page.getByRole("button", { name: "Log in" }).click();
    const skip = page.getByRole("button", { name: "Skip" });
    const navigation = page.getByRole("navigation", { name: "Navigation" });
    await expect(skip.or(navigation)).toBeVisible();
    if (await skip.isVisible()) {
        await skip.click();
    }
    await expect(navigation).toBeVisible();
}

interface Fixtures {
    /** Tracing of this test; a test that signs in itself starts it after the sign-in, with start(). */
    safeTrace: SafeTrace;
    /** A page that has signed in through the form, with tracing running from then on. */
    signedIn: Page;
}

interface WorkerFixtures {
    ui: UiEnv;
}

export const test = base.extend<Fixtures, WorkerFixtures>({
    ui: [
        async ({}, use) => {
            await use(loadEnv());
        },
        { scope: "worker" },
    ],
    safeTrace: async ({ context }, use, testInfo) => {
        const trace = new SafeTrace(context, testInfo);
        await use(trace);
        await trace.finish();
    },
    signedIn: async ({ page, ui, safeTrace }, use) => {
        await signIn(page, ui);
        await safeTrace.start();
        await use(page);
    },
});

export { expect };
