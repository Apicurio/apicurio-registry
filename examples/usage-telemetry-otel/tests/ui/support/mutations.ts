import type { Page, Route } from "@playwright/test";

import { matchesDatasourceQuery } from "../lib/grafana.mjs";

/**
 * A controlled query mutation for a negative check: every datasource request of this Grafana is passed on with the Content ID
 * condition of its query widened to match any value, as a dashboard whose Content ID control was disconnected from its query
 * would send it. Nothing is mocked: the answers are the real ones to the widened query. The route starts with start() and is
 * removed again by restore.
 */
export function ignoreContentIdSelection(
    page: Page,
    grafanaUrl: string,
): { start: () => Promise<void>; applied: () => number; restore: () => Promise<void> } {
    const matches = (url: URL): boolean => matchesDatasourceQuery(grafanaUrl, url);
    let widened = 0;
    const handler = async (route: Route): Promise<void> => {
        const body = route.request().postDataJSON() as { queries?: { expr?: unknown }[] } | null;
        for (const query of body?.queries ?? []) {
            if (typeof query.expr === "string") {
                const mutated = query.expr.replace(/(identifier_type="content_id",[^}]*?identifier_value=~)"[^"]*"/g, '$1".+"');
                if (mutated !== query.expr) {
                    widened += 1;
                    query.expr = mutated;
                }
            }
        }
        await route.continue({ postData: JSON.stringify(body) });
    };
    return {
        start: async () => {
            await page.route(matches, handler);
        },
        applied: () => widened,
        restore: () => page.unroute(matches, handler),
    };
}

/**
 * A controlled failure of only the queries for one Content ID: the datasource answers HTTP 500 to every query of this Grafana whose
 * Content ID condition is exactly that identifier, and passes everything else on. It is switched on with start(), at the step
 * that is under test.
 */
export function failContentIdQueries(
    page: Page,
    grafanaUrl: string,
    contentId: string,
): { start: () => Promise<void>; failed: () => number; restore: () => Promise<void> } {
    const matches = (url: URL): boolean => matchesDatasourceQuery(grafanaUrl, url);
    const narrowed = new RegExp(`identifier_type="content_id",[^}]*identifier_value=~"${contentId.replace(/[^0-9A-Za-z_-]/g, "")}"`);
    let failed = 0;
    const handler = async (route: Route): Promise<void> => {
        const body = route.request().postDataJSON() as { queries?: { expr?: unknown }[] } | null;
        if ((body?.queries ?? []).some((query) => typeof query.expr === "string" && narrowed.test(query.expr))) {
            failed += 1;
            await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "controlled browser-side failure" }) });
        } else {
            await route.continue();
        }
    };
    return {
        start: async () => {
            await page.route(matches, handler);
        },
        failed: () => failed,
        restore: () => page.unroute(matches, handler),
    };
}
