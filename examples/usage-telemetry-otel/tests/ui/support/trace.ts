import type { BrowserContext, TestInfo } from "@playwright/test";

/**
 * The trace keeps what is needed to understand a failure (the actions, the screenshots and the accessibility tree of the page)
 * and records no network at all. In Playwright 1.65 DOM snapshots switch on the recording of network activity, whose headers
 * carry the session cookie, so the DOM snapshot is off: the default all-snapshots mode would put the Grafana session cookie into the archive.
 * What remains can still show application text and URLs, so a trace is still to be handled as sensitive.
 */
export const SAFE_TRACE_OPTIONS = { screenshots: true, snapshots: { dom: false, aria: true, screen: true } } as const;

/**
 * Tracing for one test. It is started explicitly, after the credentials have been submitted and accepted: a failure during the
 * login form therefore has screenshot, error and report diagnostics but no trace (the privacy exception documented in the
 * guide), and the report says so instead of pretending a trace exists.
 */
export class SafeTrace {
    private started = false;

    constructor(
        private readonly context: BrowserContext,
        private readonly testInfo: TestInfo,
    ) {}

    async start(): Promise<void> {
        if (this.started) {
            return;
        }
        await this.context.tracing.start({ ...SAFE_TRACE_OPTIONS, title: this.testInfo.title });
        this.started = true;
    }

    /** Stops the trace and writes it to this path whatever the test outcome (the self-tests inspect the archive). */
    async save(path: string): Promise<void> {
        if (!this.started) {
            throw new Error("no trace is running");
        }
        this.started = false;
        await this.context.tracing.stop({ path });
    }

    /** Keeps the trace of a failed test, discards the trace of a passed one, and attaches a note when no trace could exist. */
    async finish(): Promise<void> {
        const failed = this.testInfo.status !== this.testInfo.expectedStatus;
        if (!this.started) {
            if (failed) {
                await this.testInfo.attach("no-trace", {
                    body: "No trace: the test failed before the login form was submitted and accepted. Credential entry is deliberately never traced; see the screenshot, the error and the report.",
                    contentType: "text/plain",
                });
            }
            return;
        }
        this.started = false;
        if (failed) {
            const path = this.testInfo.outputPath("trace.zip");
            await this.context.tracing.stop({ path });
            await this.testInfo.attach("trace", { path, contentType: "application/zip" });
        } else {
            await this.context.tracing.stop();
        }
    }
}
