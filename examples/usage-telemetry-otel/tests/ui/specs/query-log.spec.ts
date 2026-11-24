import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

import { startDatasourceStub } from "../support/local-server.js";
import { expectQueriesCompleted, QueryLog, type Scope } from "../support/queries.js";

// Self-tests of what the query log counts as a completed answer, in a real browser against a local stand-in endpoint (not Grafana):
// the observation is of the request lifecycle (its terminal event and its body), of the selection a query belongs to, and of the
// panels that justify an empty result.

const TABLE_QUERY = (client: string, type: string): string =>
    `max by (c) (last_over_time(m{apicurio_registry_client_id=~"${client}",apicurio_registry_identifier_type="content_id",apicurio_registry_identifier_type=~"${type}",apicurio_registry_identifier_value=~"47"}[1h]))`;

/** Starts a query from the page the way a dashboard panel does; the returned promise settles when the browser has an answer or an error. */
async function fire(page: Page, origin: string, mode: string, panel: number, expr: string, wait: boolean): Promise<void> {
    const start = (target: string, header: Record<string, string>, body: unknown): Promise<void> =>
        page.evaluate(
            async ({ target: url, header: headers, body: payload, waitFor }) => {
                const pending = fetch(url, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(payload) })
                    .then((response) => response.text())
                    .then(
                        () => undefined,
                        () => undefined,
                    );
                if (waitFor) {
                    await pending;
                }
            },
            { target, header, body, waitFor: wait },
        );
    await start(`${origin}/api/ds/query`, { "x-mode": mode, "x-panel-id": String(panel) }, { queries: [{ refId: "A", expr }] });
}

async function withStub(page: Page, body: (origin: string, log: QueryLog) => Promise<void>): Promise<void> {
    const site = await startDatasourceStub();
    try {
        await page.goto(`${site.origin}/page`);
        const log = new QueryLog(page, site.origin);
        try {
            await body(site.origin, log);
        } finally {
            log.stop();
        }
    } finally {
        await site.close();
    }
}

const scope = (since: number, panels: number[], type = "content_id"): Scope => ({
    since,
    panels,
    matches: [new RegExp(`client_id=~"probe"`), new RegExp(`identifier_type=~"${type}"`)],
});

test("an honest empty success counts as completed, an error inside HTTP 200 does not", async ({ page }) => {
    await withStub(page, async (origin, log) => {
        const since = log.mark();
        await fire(page, origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.completed(scope(since, [5]))).toBe(true);
        expect(log.failed(scope(since, [5]))).toEqual([]);
        const later = log.mark();
        await fire(page, origin, "error-in-200", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.failed(scope(later, [5])).length).toBe(1);
        expect(log.failed(scope(later, [5]))[0], "the reason names the error the datasource reported").toContain("boom");
        expect(log.completed(scope(later, [5])), "an HTTP 200 whose result carries an error is not a completed answer").toBe(false);
    });
});

test("response headers of status 200 are not a completed answer: a body that is destroyed is a failed query", async ({ page }) => {
    await withStub(page, async (origin, log) => {
        const since = log.mark();
        await fire(page, origin, "destroyed", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.failed(scope(since, [5])).length).toBe(1);
        expect(log.completed(scope(since, [5]))).toBe(false);
    });
});

test("a body that never ends stays pending and is never completed; ending the connection makes it failed", async ({ page }) => {
    const site = await startDatasourceStub();
    try {
        await page.goto(`${site.origin}/page`);
        const log = new QueryLog(page, site.origin);
        const since = log.mark();
        const headers = page.waitForResponse((response) => response.url().endsWith("/api/ds/query"));
        await fire(page, site.origin, "stalled", 5, TABLE_QUERY("probe", "content_id"), false);
        await headers;
        expect(log.pending(scope(since, [5])), "the answer is on its way").toBe(1);
        expect(log.completed(scope(since, [5]))).toBe(false);
        expect(log.failed(scope(since, [5]))).toEqual([]);
        await site.close();
        await expect.poll(() => log.failed(scope(since, [5])).length, { message: "the broken connection is an explicit failure" }).toBe(1);
        expect(log.pending(scope(since, [5]))).toBe(0);
        log.stop();
    } finally {
        await site.close();
    }
});

test("only the current selection counts: an earlier response with another type, or from before the step, never satisfies it", async ({ page }) => {
    await withStub(page, async (origin, log) => {
        // the client was chosen first, while the identifier type was still All
        await fire(page, origin, "ok-empty", 5, TABLE_QUERY("probe", ".*"), true);
        await fire(page, origin, "ok-empty", 1, TABLE_QUERY("probe", ".*"), true);
        const since = log.mark();
        await expect.poll(() => log.completed({ since: 0, panels: [1, 5], matches: [new RegExp(`client_id=~"probe"`)] }), { message: "by client alone the old answer would pass" }).toBe(true);
        expect(log.completed(scope(since, [1, 5])), "the final selection (type content_id) has not been answered yet").toBe(false);
        // the identical selection answered before the step began is older than the step
        await fire(page, origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        const afterOld = log.mark();
        expect(log.completed(scope(afterOld, [5])), "an answer from before the mark is not evidence for the step").toBe(false);
        await fire(page, origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.completed(scope(afterOld, [5]))).toBe(true);
    });
});

test("every panel that justifies the empty result needs its own successful answer, and any failed relevant query fails the step", async ({ page }) => {
    await withStub(page, async (origin, log) => {
        const since = log.mark();
        await fire(page, origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.completed(scope(since, [5]))).toBe(true);
        expect(log.completed(scope(since, [1, 5])), "the total panel has not answered").toBe(false);
        await fire(page, origin, "http-500", 1, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.failed(scope(since, [1, 5])).length).toBe(1);
        expect(log.completed(scope(since, [1, 5])), "a failed answer for the total panel is not an answer").toBe(false);
        await fire(page, origin, "ok-empty", 1, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.completed(scope(since, [1, 5]))).toBe(true);
        await expect(expectQueriesCompleted(page, log, scope(since, [1, 5]), "the probe client", 1500), "an earlier failure for the same selection still fails the step").rejects.toThrow(/failed/);
        const clean = log.mark();
        await fire(page, origin, "ok-empty", 1, TABLE_QUERY("probe", "content_id"), true);
        await fire(page, origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        await expectQueriesCompleted(page, log, scope(clean, [1, 5]), "the probe client", 5000);
    });
});

/** The same, with the requested references and the status and body the endpoint answers chosen by the test. */
async function fireWith(page: Page, origin: string, panel: number, refs: string[], answer: { status?: number; body: string }): Promise<void> {
    await page.evaluate(
        async ({ target, headers, payload }) => {
            await fetch(target, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(payload) }).then((response) => response.text());
        },
        {
            target: `${origin}/api/ds/query`,
            headers: { "x-mode": "custom", "x-panel-id": String(panel), "x-status": String(answer.status ?? 200), "x-body": answer.body },
            payload: { queries: refs.map((refId) => ({ refId, expr: TABLE_QUERY("probe", "content_id") })) },
        },
    );
}

const FRAME = (ref: string): string => `"${ref}":{"status":200,"frames":[{"schema":{"refId":"${ref}","fields":[]},"data":{"values":[]}}]}`;

test("a request of the step that is still pending keeps the step from succeeding, whatever answered before it", async ({ page }) => {
    const site = await startDatasourceStub();
    try {
        await page.goto(`${site.origin}/page`);
        const log = new QueryLog(page, site.origin);
        const since = log.mark();
        await fire(page, site.origin, "ok-empty", 1, TABLE_QUERY("probe", "content_id"), true);
        await fire(page, site.origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.completed(scope(since, [1, 5]))).toBe(true);
        const headers = page.waitForResponse((response) => response.url().endsWith("/api/ds/query") && response.request().headers()["x-mode"] === "stalled");
        await fire(page, site.origin, "stalled", 5, TABLE_QUERY("probe", "content_id"), false);
        await headers;
        expect(log.pending(scope(since, [1, 5]))).toBe(1);
        expect(log.completed(scope(since, [1, 5])), "both panels answered, but a relevant request is still on its way").toBe(false);
        await expect(expectQueriesCompleted(page, log, scope(since, [1, 5]), "the probe client", 1500), "the step does not succeed while it waits").rejects.toThrow(/still pending/);
        await site.close();
        await expect.poll(() => log.failed(scope(since, [1, 5])).length).toBe(1);
        await expect(expectQueriesCompleted(page, log, scope(since, [1, 5]), "the probe client", 1500)).rejects.toThrow(/failed/);
        log.stop();
    } finally {
        await site.close();
    }
});

test("a returned result must answer the reference that was requested, in a usable shape", async ({ page }) => {
    const cases: [string, string, RegExp][] = [
        ["another reference only", `{"results":{"B":{}}}`, /A/],
        ["an unrelated well-shaped result for another reference", `{"results":{${FRAME("B")}}}`, /A/],
        ["an empty result object", `{"results":{"A":{}}}`, /frames/],
        ["a null result", `{"results":{"A":null}}`, /A/],
        ["frames that are not a list", `{"results":{"A":{"status":200,"frames":"x"}}}`, /frames/],
    ];
    for (const [label, body, reason] of cases) {
        await withStub(page, async (origin, log) => {
            const unhandled: string[] = [];
            const listener = (error: unknown): void => void unhandled.push(String(error));
            process.on("unhandledRejection", listener);
            try {
                const since = log.mark();
                await fireWith(page, origin, 5, ["A"], { body });
                await expect.poll(() => log.pending(scope(since, [5])), { message: `${label}: the request settles` }).toBe(0);
                expect(log.completed(scope(since, [5])), `${label}: not a completed answer`).toBe(false);
                expect(log.failed(scope(since, [5])).join("|"), `${label}: a bounded reason is recorded`).toMatch(reason);
                expect(log.failed(scope(since, [5]))[0]?.length ?? 0).toBeLessThan(260);
                expect(unhandled, `${label}: no unhandled rejection`).toEqual([]);
            } finally {
                process.off("unhandledRejection", listener);
            }
        });
    }
});

test("every requested reference needs its own usable result, and a genuine empty success still counts", async ({ page }) => {
    await withStub(page, async (origin, log) => {
        const since = log.mark();
        await fireWith(page, origin, 5, ["A", "B"], { body: `{"results":{${FRAME("A")}}}` });
        await expect.poll(() => log.failed(scope(since, [5])).length).toBe(1);
        expect(log.failed(scope(since, [5]))[0], "the missing member is named").toContain("B");
        const second = log.mark();
        await fireWith(page, origin, 5, ["A", "B"], { body: `{"results":{${FRAME("A")},"B":{"status":500,"error":"boom","frames":[]}}}` });
        await expect.poll(() => log.failed(scope(second, [5])).length).toBe(1);
        expect(log.completed(scope(second, [5])), "a valid result cannot hide a failed one").toBe(false);
        const third = log.mark();
        await fireWith(page, origin, 5, ["A", "B"], { body: `{"results":{${FRAME("A")},${FRAME("B")}}}` });
        await expect.poll(() => log.completed(scope(third, [5]))).toBe(true);
        expect(log.failed(scope(third, [5]))).toEqual([]);
    });
});

test("a failure that is already recorded is in the assertion output, without waiting for a later success", async ({ page }) => {
    for (const [label, answer, shown] of [
        ["HTTP 500", { status: 500, body: `{"message":"controlled"}` }, /HTTP 500/],
        ["an error inside HTTP 200", { body: `{"results":{"A":{"status":500,"error":"boom: query failed","frames":[]}}}` }, /boom: query failed/],
    ] as const) {
        await withStub(page, async (origin, log) => {
            const since = log.mark();
            await fireWith(page, origin, 5, ["A"], answer);
            await expect.poll(() => log.failed(scope(since, [5])).length).toBe(1);
            const started = Date.now();
            await expect(expectQueriesCompleted(page, log, scope(since, [5]), "the probe client", 5000), `${label}: the recorded reason is in the output`).rejects.toThrow(shown);
            expect(Date.now() - started, `${label}: it did not wait out the timeout for a success that cannot come`).toBeLessThan(3000);
        });
    }
});

/** The same, with the queries given as they are, for requests whose queries have no usable reference. */
async function fireRaw(page: Page, origin: string, panel: number, queries: unknown[], answer: { status?: number; body: string }): Promise<void> {
    await page.evaluate(
        async ({ target, headers, payload }) => {
            await fetch(target, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(payload) }).then((response) => response.text());
        },
        {
            target: `${origin}/api/ds/query`,
            headers: { "x-mode": "custom", "x-panel-id": String(panel), "x-status": String(answer.status ?? 200), "x-body": answer.body },
            payload: { queries },
        },
    );
}

const ERROR_MARKER = "data-testid Panel status error";

test("a request that starts during the final error-mark assertion keeps the step from succeeding", async ({ page }) => {
    const site = await startDatasourceStub();
    try {
        await page.goto(`${site.origin}/page`);
        await page.evaluate((id) => {
            document.body.innerHTML = `<div data-testid="${id}"></div>`;
        }, ERROR_MARKER);
        const log = new QueryLog(page, site.origin);
        const since = log.mark();
        await fire(page, site.origin, "ok-empty", 1, TABLE_QUERY("probe", "content_id"), true);
        await fire(page, site.origin, "ok-empty", 5, TABLE_QUERY("probe", "content_id"), true);
        await expect.poll(() => log.completed(scope(since, [1, 5]))).toBe(true);
        // the real locator is used unchanged; the spy only says that the helper has reached the error-mark assertion, which cannot
        // pass while the mark is on the page
        let reached: () => void = () => undefined;
        const atMark = new Promise<void>((resolve) => {
            reached = resolve;
        });
        const original = page.getByTestId.bind(page);
        (page as { getByTestId: Page["getByTestId"] }).getByTestId = (id) => {
            if (id === ERROR_MARKER) {
                reached();
            }
            return original(id);
        };
        const outcome = expectQueriesCompleted(page, log, scope(since, [1, 5]), "the probe client", 3000).then(
            () => "succeeded",
            (error: unknown) => `rejected: ${String(error)}`,
        );
        await atMark;
        const headers = page.waitForResponse((response) => response.url().endsWith("/api/ds/query") && response.request().headers()["x-mode"] === "stalled");
        await fire(page, site.origin, "stalled", 5, TABLE_QUERY("probe", "content_id"), false);
        await headers;
        await page.evaluate(() => {
            document.body.innerHTML = "";
        });
        const result = await outcome;
        expect(log.pending(scope(since, [1, 5])), "the request that started during the assertion is still on its way").toBe(1);
        expect(result, "the step must not succeed while that request is pending").toMatch(/^rejected: .*still pending/s);
        log.stop();
    } finally {
        await site.close();
    }
});

test("a frame must have the minimal envelope Grafana reads; empty lists and genuinely empty frames still count", async ({ page }) => {
    const bad: [string, string][] = [
        ["a null frame", `[null]`],
        ["an empty object as a frame", `[{}]`],
        ["a list as a frame", `[[]]`],
        ["a frame without schema fields", `[{"schema":{},"data":{"values":[]}}]`],
        ["a frame without data", `[{"schema":{"fields":[]}}]`],
        ["a frame without data values", `[{"schema":{"fields":[]},"data":{}}]`],
        ["a frame whose fields are not a list", `[{"schema":{"fields":"x"},"data":{"values":[]}}]`],
    ];
    for (const [label, frames] of bad) {
        await withStub(page, async (origin, log) => {
            const since = log.mark();
            await fireWith(page, origin, 5, ["A"], { body: `{"results":{"A":{"status":200,"frames":${frames}}}}` });
            await expect.poll(() => log.failed(scope(since, [5])).length, { message: label }).toBe(1);
            expect(log.completed(scope(since, [5])), `${label}: not a completed answer`).toBe(false);
            expect(log.failed(scope(since, [5]))[0], `${label}: the reason names the frame`).toMatch(/frame/);
        });
    }
    for (const [label, frames] of [
        ["no frames at all", `[]`],
        ["a genuinely empty frame", `[{"schema":{"refId":"A","fields":[]},"data":{"values":[]}}]`],
        ["a frame with data", `[{"schema":{"refId":"A","fields":[{"name":"Time","type":"time"},{"name":"Value","type":"number"}]},"data":{"values":[[1],[2]]}}]`],
    ] as const) {
        await withStub(page, async (origin, log) => {
            const since = log.mark();
            await fireWith(page, origin, 5, ["A"], { body: `{"results":{"A":{"status":200,"frames":${frames}}}}` });
            await expect.poll(() => log.completed(scope(since, [5])), { message: label }).toBe(true);
            expect(log.failed(scope(since, [5]))).toEqual([]);
        });
    }
});

test("a recorded reason is bounded even when the requested reference is very long", async ({ page }) => {
    const reference = "R".repeat(4096);
    for (const [label, body] of [
        ["a missing result", `{"results":{}}`],
        ["an error for the reference", `{"results":{"${reference}":{"status":500,"error":"${"E".repeat(4096)}","frames":[]}}}`],
        ["a result that is not an object", `{"results":{"${reference}":1}}`],
    ] as const) {
        await withStub(page, async (origin, log) => {
            const since = log.mark();
            await fireWith(page, origin, 5, [reference], { body });
            await expect.poll(() => log.failed(scope(since, [5])).length, { message: label }).toBe(1);
            const reason = log.failed(scope(since, [5]))[0] ?? "";
            expect(reason.length, `${label}: the whole recorded diagnostic is bounded`).toBeLessThanOrEqual(260);
            expect(reason, `${label}: still says what is wrong`).toMatch(/reference|result/);
        });
    }
});

test("a matching expression with no string reference is a terminal failure, and an error for an unrequested reference is not this request's failure", async ({ page }) => {
    await withStub(page, async (origin, log) => {
        const since = log.mark();
        for (const queries of [[{ expr: TABLE_QUERY("probe", "content_id") }], [{ refId: 7, expr: TABLE_QUERY("probe", "content_id") }], [{ refId: null, expr: TABLE_QUERY("probe", "content_id") }]]) {
            await fireRaw(page, origin, 5, queries, { body: `{"results":{${FRAME("A")}}}` });
        }
        await expect.poll(() => log.failed(scope(since, [5])).length).toBe(3);
        expect(log.pending(scope(since, [5]))).toBe(0);
        expect(log.failed(scope(since, [5]))[0]).toContain("names no query reference");
        expect(log.completed(scope(since, [5]))).toBe(false);
        const second = log.mark();
        await fireWith(page, origin, 5, ["A"], { body: `{"results":{${FRAME("A")},"B":{"status":500,"error":"unrelated","frames":[]}}}` });
        await expect.poll(() => log.completed(scope(second, [5])), { message: "the requested reference A is answered; the error of the unrequested B is not this request's" }).toBe(true);
        expect(log.failed(scope(second, [5]))).toEqual([]);
    });
});
