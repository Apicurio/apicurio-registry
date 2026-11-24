import { expect, type Page, type Request } from "@playwright/test";

import { matchesDatasourceQuery } from "../lib/grafana.mjs";
import { errorMarkers } from "./dashboard.js";
import { EXPECT_TIMEOUT_MS } from "./env.js";

/** The longest diagnostic that is recorded for a failed query, whatever the reference or the datasource's own message was. */
const REASON_LIMIT = 200;
/** What of a requested reference is shown in a diagnostic. */
const shown = (reference: string): string => (reference.length > 40 ? `${reference.slice(0, 40)}...` : reference);

type Outcome = "pending" | "ok" | "failed";

interface Query {
    sequence: number;
    /** The query references the request asked for (the refId of each of its queries); a result is judged against these. */
    references: string[];
    /** The panel that sent it (the x-panel-id header Grafana sets), when there is one. */
    panel: number | undefined;
    expressions: string[];
    outcome: Outcome;
    /** Why it failed: a request failure, a status or the error the datasource reported; never a header or a cookie. */
    why: string;
}

/** Which queries are evidence for a step: sent after `since`, by one of these panels, with an expression matching every pattern. */
export interface Scope {
    since: number;
    panels: number[];
    matches: RegExp[];
}

/**
 * What the datasource answered while a flow ran, tracked per request through to its terminal event. A query counts as completed only
 * when the request finished, the HTTP status is 200 and the body answers every query reference the request asked for with a usable
 * result (an error reported for a reference that was not requested is not this request's failure); response headers alone are not
 * an answer, because a body can still fail or never end. An empty table is also what a failed query leaves
 * behind, so an expectation of "nothing" is evidence only when the queries of the current selection, for the panels that show it,
 * completed. This observes requests of the page; it is not a proxy and changes nothing.
 */
export class QueryLog {
    private readonly queries = new Map<Request, Query>();
    private sequence = 0;
    private readonly onRequest: (request: Request) => void;
    private readonly onFailed: (request: Request) => void;
    private readonly onFinished: (request: Request) => void;

    constructor(
        private readonly page: Page,
        grafanaUrl: string,
    ) {
        this.onRequest = (request) => {
            if (!matchesDatasourceQuery(grafanaUrl, new URL(request.url()))) {
                return;
            }
            const body = request.postDataJSON() as { queries?: { expr?: unknown; refId?: unknown }[] } | null;
            const panelHeader = Number(request.headers()["x-panel-id"]);
            this.sequence += 1;
            this.queries.set(request, {
                sequence: this.sequence,
                references: [...new Set((body?.queries ?? []).flatMap((query) => (typeof query.refId === "string" ? [query.refId] : [])))],
                panel: Number.isInteger(panelHeader) ? panelHeader : undefined,
                expressions: (body?.queries ?? []).flatMap((query) => (typeof query.expr === "string" ? [query.expr] : [])),
                outcome: "pending",
                why: "",
            });
        };
        this.onFailed = (request) => {
            const query = this.queries.get(request);
            if (query !== undefined) {
                query.outcome = "failed";
                query.why = `request failed: ${request.failure()?.errorText ?? "unknown"}`;
            }
        };
        this.onFinished = (request) => {
            const query = this.queries.get(request);
            if (query !== undefined) {
                void this.judge(request, query);
            }
        };
        page.on("request", this.onRequest);
        page.on("requestfailed", this.onFailed);
        page.on("requestfinished", this.onFinished);
    }

    /**
     * The finished request's answer, settled exactly once: it is completed when the HTTP status is 200 and the body answers every
     * reference the request asked for with a usable result; anything else, and any failure of this judgment itself, is failed with a
     * short reason. Nothing of the body, a header or a cookie is kept.
     */
    private async judge(request: Request, query: Query): Promise<void> {
        let failure: string | undefined;
        try {
            failure = await this.problemWith(request, query.references);
        } catch (error) {
            failure = `the answer could not be judged: ${String(error).slice(0, 120)}`;
        }
        query.outcome = failure === undefined ? "ok" : "failed";
        query.why = (failure ?? "").slice(0, REASON_LIMIT);
    }

    /** What is wrong with the answer of a finished request, or undefined when every requested reference has a usable result. */
    private async problemWith(request: Request, references: string[]): Promise<string | undefined> {
        const response = await request.response();
        if (response === null) {
            return "no response";
        }
        if (response.status() !== 200) {
            return `HTTP ${response.status()}`;
        }
        let body: unknown;
        try {
            body = await response.json();
        } catch {
            return "the body is not readable JSON";
        }
        const results = typeof body === "object" && body !== null ? (body as { results?: unknown }).results : undefined;
        if (typeof results !== "object" || results === null || Array.isArray(results)) {
            return "the body has no datasource results object";
        }
        if (references.length === 0) {
            return "the request names no query reference, so no result can be matched to it";
        }
        for (const reference of references) {
            const result: unknown = (results as Record<string, unknown>)[reference];
            if (result === undefined) {
                return `no result for the requested reference ${shown(reference)}`;
            }
            if (typeof result !== "object" || result === null || Array.isArray(result)) {
                return `the result for reference ${shown(reference)} is not an object`;
            }
            const { error, status, frames } = result as { error?: unknown; status?: unknown; frames?: unknown };
            if (error !== undefined) {
                return `the datasource reported an error for reference ${shown(reference)} inside HTTP 200: ${typeof error === "string" ? error : "an error object"}`;
            }
            if (status !== undefined && status !== 200) {
                return `the result for reference ${shown(reference)} has status ${String(status).slice(0, 20)}`;
            }
            if (!Array.isArray(frames)) {
                return `the result for reference ${shown(reference)} has no frames list`;
            }
            // the minimal envelope Grafana reads from a frame (checked against its real answers): schema.fields and data.values are lists
            for (const [position, frame] of frames.entries()) {
                const { schema, data } = (typeof frame === "object" && frame !== null && !Array.isArray(frame) ? frame : {}) as { schema?: { fields?: unknown } | null; data?: { values?: unknown } | null };
                if (!Array.isArray(schema?.fields) || !Array.isArray(data?.values)) {
                    return `frame ${position} of reference ${shown(reference)} lacks schema.fields or data.values lists`;
                }
            }
        }
        return undefined;
    }

    /** The position after which a step's queries are counted: take it right before the action that starts the step. */
    mark(): number {
        return this.sequence;
    }

    private inScope(scope: Scope): Query[] {
        return [...this.queries.values()].filter(
            (query) =>
                query.sequence > scope.since &&
                query.panel !== undefined &&
                scope.panels.includes(query.panel) &&
                query.expressions.some((expression) => scope.matches.every((pattern) => pattern.test(expression))),
        );
    }

    /** Every panel of the scope has a completed answer for the selection, and no relevant request is still on its way. */
    completed(scope: Scope): boolean {
        const relevant = this.inScope(scope);
        return relevant.every((query) => query.outcome !== "pending") && scope.panels.every((panel) => relevant.some((query) => query.panel === panel && query.outcome === "ok"));
    }

    /** The reasons of the relevant queries that failed. */
    failed(scope: Scope): string[] {
        return this.inScope(scope)
            .filter((query) => query.outcome === "failed")
            .map((query) => `panel ${query.panel}: ${query.why}`);
    }

    /** How many relevant queries have not reached a terminal outcome yet. */
    pending(scope: Scope): number {
        return this.inScope(scope).filter((query) => query.outcome === "pending").length;
    }

    /** "failed" as soon as a relevant query failed, "completed" when the scope is answered, otherwise "waiting". */
    state(scope: Scope): "failed" | "completed" | "waiting" {
        if (this.failed(scope).length > 0) {
            return "failed";
        }
        return this.completed(scope) ? "completed" : "waiting";
    }

    /** What the log holds for the scope, for the message of a step that did not get its answers. */
    explain(scope: Scope): string {
        const relevant = this.inScope(scope);
        const answered = scope.panels.filter((panel) => relevant.some((query) => query.panel === panel && query.outcome === "ok"));
        const failed = this.failed(scope);
        return `answered panels ${answered.join(", ") || "none"} of ${scope.panels.join(", ")}; ${this.pending(scope)} relevant request(s) still pending; failed: ${failed.join("; ") || "none"}`;
    }

    stop(): void {
        this.page.off("request", this.onRequest);
        this.page.off("requestfailed", this.onFailed);
        this.page.off("requestfinished", this.onFinished);
    }
}

/**
 * The step's evidence for an empty or narrowed result: every panel of the scope answered successfully for the current selection, no
 * relevant request is still pending, no relevant query failed (a failure that is recorded ends the wait at once and is part of the
 * output), and no panel shows its native error mark. All of it is waited for within ONE deadline, and the outcomes are looked at once
 * more, synchronously, at the moment of returning: the error-mark assertion is a real browser wait during which another matching
 * request may have started, so a snapshot taken before it is not enough. Requests that have not started yet are not known, and no
 * quiet period is waited for.
 */
export async function expectQueriesCompleted(page: Page, queries: QueryLog, scope: Scope, what: string, timeoutMs?: number): Promise<void> {
    const deadline = Date.now() + (timeoutMs ?? EXPECT_TIMEOUT_MS);
    for (;;) {
        try {
            await expect
                .poll(() => queries.state(scope), { message: `a successful datasource answer for ${what}`, timeout: Math.max(deadline - Date.now(), 1) })
                .not.toBe("waiting");
        } catch (error) {
            throw new Error(`no complete datasource answer for ${what}: ${queries.explain(scope)}`, { cause: error });
        }
        expect(queries.failed(scope), `no datasource query for ${what} failed`).toEqual([]);
        await expect(errorMarkers(page)).toHaveCount(0, { timeout: Math.max(deadline - Date.now(), 1) });
        // the return boundary: nothing is awaited between this look and the return
        const state = queries.state(scope);
        if (state === "completed") {
            return;
        }
        expect(queries.failed(scope), `no datasource query for ${what} failed`).toEqual([]);
        if (Date.now() >= deadline) {
            throw new Error(`no complete datasource answer for ${what}: ${queries.explain(scope)}`);
        }
    }
}
