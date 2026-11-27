import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

import { startQueryEchoServer } from "../support/local-server.js";
import { failDatasourceQueries } from "../support/dashboard.js";
import { ignoreContentIdSelection } from "../support/mutations.js";

// Self-tests of the interception helpers, with no Grafana: the helpers are given a Grafana base URL, installed with Playwright's real
// route matching in a real browser, and a page posts to a local stand-in endpoint at the root path and below /grafana. This proves
// which requests each helper touches for a root and for a prefixed base URL; it is helper-level proof against a stand-in, not a
// test of a Grafana served behind a reverse proxy.

const QUERY = { queries: [{ expr: 'x{apicurio_registry_identifier_type="content_id",apicurio_registry_identifier_type=~"content_id",apicurio_registry_identifier_value=~"47"}' }] };

/** Posts the query and returns the status and the expression the endpoint received, after any route changed the request. */
async function post(page: Page, url: string): Promise<{ status: number; received: string }> {
    return page.evaluate(
        async ({ target, body }) => {
            const response = await fetch(target, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
            const text = await response.text();
            let received = "";
            try {
                const echoed = JSON.parse((JSON.parse(text) as { received: string }).received) as { queries: { expr: string }[] };
                received = echoed.queries[0]?.expr ?? "";
            } catch {
                received = text;
            }
            return { status: response.status, received };
        },
        { target: url, body: QUERY },
    );
}

for (const base of ["", "/grafana"]) {
    const label = base === "" ? "a Grafana at the root" : "a Grafana below /grafana";

    test(`the Content ID mutation touches exactly the datasource endpoint of ${label}`, async ({ page }) => {
        const site = await startQueryEchoServer();
        try {
            await page.goto(`${site.origin}/page`);
            const mutation = ignoreContentIdSelection(page, `${site.origin}${base}`);
            await mutation.start();
            const own = await post(page, `${site.origin}${base}/api/ds/query`);
            expect(own.status).toBe(200);
            expect(own.received, "the query of this Grafana was widened").toContain('identifier_value=~".+"');
            expect(mutation.applied()).toBe(1);
            const other = await post(page, `${site.origin}${base === "" ? "/grafana" : ""}/api/ds/query`);
            expect(other.received, "the endpoint of another path of the same host is not touched").toContain('identifier_value=~"47"');
            expect(mutation.applied()).toBe(1);
            await mutation.restore();
            expect((await post(page, `${site.origin}${base}/api/ds/query`)).received, "removed again").toContain('identifier_value=~"47"');
        } finally {
            await site.close();
        }
    });

    test(`the controlled failure answers HTTP 500 for the datasource endpoint of ${label} only`, async ({ page }) => {
        const site = await startQueryEchoServer();
        try {
            await page.goto(`${site.origin}/page`);
            const restore = await failDatasourceQueries(page, `${site.origin}${base}`);
            expect((await post(page, `${site.origin}${base}/api/ds/query`)).status).toBe(500);
            expect((await post(page, `${site.origin}${base === "" ? "/grafana" : ""}/api/ds/query`)).status, "another path of the same host").toBe(200);
            await restore();
            expect((await post(page, `${site.origin}${base}/api/ds/query`)).status).toBe(200);
        } finally {
            await site.close();
        }
    });
}
