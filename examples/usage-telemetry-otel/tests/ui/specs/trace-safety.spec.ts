import { expect, test } from "@playwright/test";
import type { BrowserContext, Page, TestInfo } from "@playwright/test";

import { SafeTrace } from "../support/trace.js";
import { MARKERS, startMarkerServer } from "../support/local-server.js";
import { inspectTrace } from "../support/zip.js";

// Self-tests of the diagnostics: they need no Grafana and no stack. Each looks inside a real trace archive for the markers of a
// credential, a session cookie and a request header, and for the network records and stored bodies that would carry them.

async function submitCredentials(page: Page, url: string): Promise<void> {
    await page.goto(`${url}/login`);
    await page.getByLabel("user").fill("admin");
    await page.getByLabel("password").fill(MARKERS.password);
    await page.getByRole("button", { name: "Log in" }).click();
    await expect(page.getByRole("heading", { name: "Welcome" })).toBeVisible();
}

test("the safe trace keeps actions, screenshots and the accessibility tree and records no credential, cookie, header or network", async ({ page, context }, testInfo) => {
    const site = await startMarkerServer();
    try {
        await submitCredentials(page, site.url);
        const trace = new SafeTrace(context, testInfo);
        await trace.start();
        await page.reload();
        await expect(page.getByText(MARKERS.payload)).toBeVisible();
        const path = testInfo.outputPath("safe-trace.zip");
        await trace.save(path);

        const facts = inspectTrace(path);
        for (const [what, marker] of [["password", MARKERS.password], ["session cookie", MARKERS.cookie], ["request header", MARKERS.header]] as const) {
            expect(facts.text.includes(marker), `the ${what} marker must not be in the archive`).toBe(false);
        }
        expect(facts.networkRecords, "no network record").toBe(0);
        expect(facts.resources, "no stored response body or snapshot resource").toBe(0);
        expect(facts.eventTypes.has("frame-snapshot"), "no DOM snapshot").toBe(false);
        // and it is still useful
        expect(facts.eventTypes.has("before"), "actions are recorded").toBe(true);
        expect(facts.eventTypes.has("aria-snapshot"), "the accessibility tree is recorded").toBe(true);
        expect(facts.actions.has("Page.reload"), "the reload action is named").toBe(true);
        expect(facts.actions.has("Frame.expect"), "the assertion is recorded").toBe(true);
        expect(facts.images, "screenshots are kept").toBeGreaterThan(0);
        // the limit that remains, stated by a check instead of a promise: text that is visible on the page is kept
        expect(facts.text.includes(MARKERS.payload), "visible application text is retained").toBe(true);
    } finally {
        await site.close();
    }
});

test("control: the default trace of the same flow does carry the cookie and the header, so the inspection can see them", async ({ page, context }, testInfo) => {
    const site = await startMarkerServer();
    try {
        await submitCredentials(page, site.url);
        await context.tracing.start({ screenshots: true, snapshots: true });
        await page.reload();
        await expect(page.getByText(MARKERS.payload)).toBeVisible();
        const path = testInfo.outputPath("default-trace.zip");
        await context.tracing.stop({ path });
        const facts = inspectTrace(path);
        expect(facts.text.includes(MARKERS.cookie), "the default trace records the session cookie").toBe(true);
        expect(facts.text.includes(MARKERS.header), "the default trace records request headers").toBe(true);
        expect(facts.networkRecords).toBeGreaterThan(0);
        expect(facts.text.includes(MARKERS.password), "the password was typed before tracing started").toBe(false);
    } finally {
        await site.close();
    }
});

test("a failure before the login is accepted leaves no trace, says so in the report and never starts one", async () => {
    const calls: string[] = [];
    const context = {
        tracing: {
            start: async () => void calls.push("start"),
            stop: async () => void calls.push("stop"),
        },
    } as unknown as BrowserContext;
    const attached: { name: string; body: string }[] = [];
    const testInfo = {
        title: "login",
        status: "failed",
        expectedStatus: "passed",
        outputPath: (name: string) => name,
        attach: async (name: string, options: { body?: string }) => void attached.push({ name, body: options.body ?? "" }),
    } as unknown as TestInfo;
    const trace = new SafeTrace(context, testInfo);
    await trace.finish();
    expect(calls, "tracing was never started or stopped").toEqual([]);
    expect(attached.map((entry) => entry.name)).toEqual(["no-trace"]);
    expect(attached[0]?.body).toMatch(/before the login form was submitted and accepted/);
});

test("after the login is accepted a failed test keeps its trace and a passed one discards it", async () => {
    const run = async (status: "failed" | "passed") => {
        const calls: string[] = [];
        const context = {
            tracing: {
                start: async () => void calls.push("start"),
                stop: async (options?: { path?: string }) => void calls.push(options?.path === undefined ? "stop-discard" : "stop-save"),
            },
        } as unknown as BrowserContext;
        const attached: string[] = [];
        const testInfo = {
            title: "t",
            status,
            expectedStatus: "passed",
            outputPath: (name: string) => name,
            attach: async (name: string) => void attached.push(name),
        } as unknown as TestInfo;
        const trace = new SafeTrace(context, testInfo);
        await trace.start();
        await trace.start();
        await trace.finish();
        return { calls, attached };
    };
    expect(await run("failed")).toEqual({ calls: ["start", "stop-save"], attached: ["trace"] });
    expect(await run("passed")).toEqual({ calls: ["start", "stop-discard"], attached: [] });
});
