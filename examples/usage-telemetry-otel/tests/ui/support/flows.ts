import { expect, type Page } from "@playwright/test";

import {
    changeSelection,
    DATA_PANEL_IDS,
    errorMarkers,
    expectRows,
    expectUrlValues,
    failDatasourceQueries,
    noValueText,
    openDashboard,
    PANEL,
    panelBox,
    panelTitle,
    panelValue,
    type View,
} from "./dashboard.js";
import { filterPlan } from "../lib/plan.mjs";
import type { UiEnv } from "./env.js";
import { expectQueriesCompleted, QueryLog, type Scope } from "./queries.js";
import { type RunState, type Selection, selectRows, sortedRows, totalOf } from "./state.js";

/** Takes a named screenshot of the page as it is; the caller decides whether it is attached, written or both. */
export type Shot = (name: "overview" | "filtered") => Promise<void>;

export interface Seeded {
    ui: UiEnv;
    state: RunState;
}

export function seeded(ui: UiEnv): Seeded {
    if (ui.state === undefined) {
        throw new Error("these tests need the seeded run state (UI_MODE=seeded)");
    }
    return { ui, state: ui.state };
}

/** The dashboard at the run's exact range: from the recorded start to the evaluation time in milliseconds, nothing padded. */
export function seededView(context: Seeded, narrow: Partial<View> = {}): View {
    return {
        grafanaUrl: context.ui.grafanaUrl,
        datasourceUid: context.ui.datasourceUid,
        instance: context.state.instanceId,
        fromMs: context.state.fromMs,
        toMs: context.state.evaluationMs,
        globalIds: context.state.globalIds,
        contentIds: context.state.contentIds,
        ...narrow,
    };
}

const escapeRegExp = (text: string): string => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** The panels whose queries justify an empty result: the table and the total, the two the empty expectations read. */
const EVIDENCE_PANELS = [PANEL.total, PANEL.table];

/**
 * Patterns that a query of the Content ID step must match to belong to it: the client, the global ID leg with the chosen global ID
 * and the content ID leg with the narrowed Content ID, both under the identifier type that is selected (content_id).
 */
function contentStepMatches(client: string, globalId: string, contentId: string): RegExp[] {
    return [
        new RegExp(`client_id=~"${escapeRegExp(client)}"`),
        new RegExp(`identifier_type="global_id",[^}]*identifier_type=~"content_id",[^}]*identifier_value=~"${escapeRegExp(globalId)}"`),
        new RegExp(`identifier_type="content_id",[^}]*identifier_type=~"content_id",[^}]*identifier_value=~"${escapeRegExp(contentId)}"`),
    ];
}

/**
 * The same, where the selection may legitimately show nothing: then the documented no-observation texts are what must show, and
 * only after the datasource has answered the queries of that very selection (the client, the chosen global ID and the narrowed
 * Content ID under the selected type, started after the step began) for the panels that show the result, without an error.
 */
async function expectSelectionOrNone(page: Page, context: Seeded, selection: Selection, queries: QueryLog, scope: Scope, what: string): Promise<void> {
    const rows = selectRows(context.state, selection);
    if (rows.length > 0) {
        await expectSelection(page, context, selection);
        return;
    }
    await expectRows(page, PANEL.table, []);
    await expectQueriesCompleted(page, queries, scope, what);
    await expect(panelBox(page, PANEL.table)).toContainText(noValueText(PANEL.table));
    await expect(panelValue(page, PANEL.total)).toHaveText(noValueText(PANEL.total));
}

/** What the table and the first stat must show for a selection of the run's own series. */
async function expectSelection(page: Page, context: Seeded, selection: Selection): Promise<void> {
    const rows = selectRows(context.state, selection);
    if (rows.length === 0) {
        throw new Error("the test's own selection is empty; the run state does not have the series this step needs");
    }
    await expectRows(page, PANEL.table, sortedRows(rows));
    await expect(panelValue(page, PANEL.total)).toHaveText(String(totalOf(rows)));
}

/**
 * The rate chart has no value to compare in a seeded run (its numbers are covered by the shell oracle), but a picture must not
 * be taken while it is still loading: it must show either its own no-rate text or a legend of the operations of the selection.
 */
async function expectRatePanelSettled(page: Page, operations: string[]): Promise<void> {
    const box = panelBox(page, PANEL.rate);
    const noRate = box.getByText(noValueText(PANEL.rate));
    const legend = box.getByText(new RegExp(`^(${operations.join("|")})$`));
    await expect(noRate.or(legend).first()).toBeVisible();
}

export async function overviewFlow(page: Page, context: Seeded, shot: Shot): Promise<void> {
    const { state } = context;
    await openDashboard(page, seededView(context));
    await expectSelection(page, context, { globalIds: state.globalIds, contentIds: state.contentIds });
    await expect(panelValue(page, PANEL.overflow)).toHaveText(noValueText(PANEL.overflow));
    // The export age is read at the end of a finished range: a duration, never a health word
    await expect(panelValue(page, PANEL.exportAge)).toHaveText(/^\d+(\.\d+)? ?(ms|s|min|hour|hours|day|days)$/);
    await expect(panelBox(page, PANEL.exportAge)).toContainText(panelTitle(PANEL.exportAge));
    for (const id of [PANEL.total, PANEL.overflow, PANEL.exportAge]) {
        await expect(panelBox(page, id)).not.toContainText(/healthy|active|alive|stale/i);
    }
    // The run's Registry process is listed once as a candidate; other processes of a long-lived stack may be listed too
    await expect
        .poll(async () => (await panelBox(page, PANEL.processes).getByRole("gridcell", { name: state.instanceId, exact: true }).count()))
        .toBe(1);
    await expect(errorMarkers(page)).toHaveCount(0);
    await expectRatePanelSettled(page, [...new Set(state.series.map((row) => row.operation))]);
    await shot("overview");
}

/** Client, identifier type and typed identifier filters, each changed with the real controls and checked against the rows. */
export async function filteredFlow(
    page: Page,
    context: Seeded,
    shot: Shot,
    trail: string[] = [],
    beforeContentControl: () => Promise<void> = async () => undefined,
): Promise<void> {
    const { state } = context;
    const { plan } = filterPlan(state);
    if (plan === undefined) {
        throw new Error("this run state cannot run the filter flow; the runner checks that before the browser starts");
    }
    const queries = new QueryLog(page, context.ui.grafanaUrl);
    try {
        await openDashboard(page, seededView(context));
        await expectSelection(page, context, { globalIds: state.globalIds, contentIds: state.contentIds });

        // 1. Client: All is cleared explicitly, then the client is chosen
        await changeSelection(page, "client", { uncheck: ["All"], check: [plan.client] });
        await expectUrlValues(page, "var-client", [plan.client]);
        const byClient: Selection = { clients: [plan.client], globalIds: state.globalIds, contentIds: state.contentIds };
        await expectSelection(page, context, byClient);
        trail.push("client");

        // 2. Identifier type: global IDs only
        await changeSelection(page, "type", { uncheck: ["All"], check: ["global_id"] });
        await expectUrlValues(page, "var-type", ["global_id"]);
        const byType: Selection = { ...byClient, types: ["global_id"] };
        await expectSelection(page, context, byType);
        trail.push("type");

        // 3. Typed identifier: one global ID, the others of the run unchecked
        await changeSelection(page, "global_id", { uncheck: plan.otherGlobals });
        await expectUrlValues(page, "var-global_id", [plan.chosenGlobal]);
        const byGlobal: Selection = { ...byType, globalIds: [plan.chosenGlobal] };
        await expectSelection(page, context, byGlobal);
        trail.push("global");
        await expect(errorMarkers(page)).toHaveCount(0);
        await expectRatePanelSettled(page, [...new Set(selectRows(state, byGlobal).map((row) => row.operation))]);
        await shot("filtered");

        // 4. Switching the type shows the content ID rows of the client
        await changeSelection(page, "type", { uncheck: ["global_id"], check: ["content_id"] });
        await expectUrlValues(page, "var-type", ["content_id"]);
        const byContentType: Selection = { ...byGlobal, types: ["content_id"] };
        await expectSelection(page, context, byContentType);
        trail.push("content-type");

        // 5. The Content ID control itself. The Content ID to narrow to was chosen from the run state when the input was prepared:
        //    one of two that have rows, or one that has no row here, which must empty the table. Either way the expected rows
        //    differ from the ones before, so an ignored selection cannot pass, and an empty table counts only after the
        //    datasource answered the narrowed query without an error.
        const after: Selection = { ...byContentType, contentIds: [plan.contentTarget] };
        await beforeContentControl();
        const step: Scope = { since: queries.mark(), panels: EVIDENCE_PANELS, matches: contentStepMatches(plan.client, plan.chosenGlobal, plan.contentTarget) };
        await changeSelection(page, "content_id", { uncheck: plan.otherContents });
        await expectUrlValues(page, "var-content_id", [plan.contentTarget]);
        await expectSelectionOrNone(page, context, after, queries, step, `the narrowed Content ID ${plan.contentTarget} of client ${plan.client}`);
        trail.push("content");
        await changeSelection(page, "content_id", { check: plan.otherContents });
        await expectSelection(page, context, byContentType);
        trail.push("content-restored");
    } finally {
        queries.stop();
    }
}

/**
 * The same number can be a global ID of one schema version and a content ID of another. When the run has such a number with
 * a series of each type, this opens exactly that number, shows both rows, and shows that choosing a type keeps the other
 * type's row out. The result says whether the case ran, because it depends on the data of the run and is not universal.
 */
export async function sameNumberFlow(page: Page, context: Seeded): Promise<string> {
    const { state } = context;
    const ofType = (type: string): Set<string> => new Set(state.series.filter((row) => row.identifierType === type).map((row) => row.identifierValue));
    const globals = ofType("global_id");
    const overlap = [...ofType("content_id")].filter((id) => globals.has(id)).sort();
    const [number] = overlap;
    if (number === undefined) {
        return "not run: no number is both a global ID and a content ID with a series in this run";
    }
    const both: Selection = { globalIds: [number], contentIds: [number] };
    const view = seededView(context, { globalIds: [number], contentIds: [number] });
    await openDashboard(page, view);
    await expectSelection(page, context, both);
    const types = new Set(selectRows(state, both).map((row) => row.identifierType));
    if (!types.has("global_id") || !types.has("content_id")) {
        throw new Error(`number ${number} must show a global ID row and a content ID row`);
    }
    await changeSelection(page, "type", { uncheck: ["All"], check: ["global_id"] });
    await expectSelection(page, context, { ...both, types: ["global_id"] });
    await changeSelection(page, "type", { uncheck: ["global_id"], check: ["content_id"] });
    await expectSelection(page, context, { ...both, types: ["content_id"] });
    return `ran for number ${number} (${overlap.length} such number${overlap.length === 1 ? "" : "s"} in the run)`;
}

/** A selection that matches nothing is no data, not an error. */
export async function noMatchFlow(page: Page, context: Seeded): Promise<void> {
    const { state } = context;
    const queries = new QueryLog(page, context.ui.grafanaUrl);
    await openDashboard(page, seededView(context));
    await changeSelection(page, "client", { uncheck: ["All"], check: [state.probeClient] });
    const finalSelection: Scope = {
        since: queries.mark(),
        panels: EVIDENCE_PANELS,
        matches: [new RegExp(`client_id=~"${escapeRegExp(state.probeClient)}"`), new RegExp(`identifier_type=~"content_id"`)],
    };
    await changeSelection(page, "type", { uncheck: ["All"], check: ["content_id"] });
    await expectUrlValues(page, "var-client", [state.probeClient]);
    await expectUrlValues(page, "var-type", ["content_id"]);
    const selection: Selection = { clients: [state.probeClient], types: ["content_id"], globalIds: state.globalIds, contentIds: state.contentIds };
    if (selectRows(state, selection).length !== 0) {
        throw new Error("the probe client has content ID series; this selection is not empty, the test needs another one");
    }
    await expectQueriesCompleted(page, queries, finalSelection, `the probe client ${state.probeClient} with identifier type content_id`);
    queries.stop();
    await expect(panelBox(page, PANEL.table)).toContainText(noValueText(PANEL.table));
    await expect(panelValue(page, PANEL.total)).toHaveText(noValueText(PANEL.total));
    await expectRows(page, PANEL.table, []);
    await expect(errorMarkers(page)).toHaveCount(0);
}

/** Every datasource query fails inside the browser: native error marks and the unknown presentation, then the route is removed. */
export async function errorFlow(page: Page, context: Seeded): Promise<void> {
    await openDashboard(page, seededView(context));
    await expectSelection(page, context, { globalIds: context.state.globalIds, contentIds: context.state.contentIds });
    await expect(errorMarkers(page)).toHaveCount(0);

    const restore = await failDatasourceQueries(page, context.ui.grafanaUrl);
    await page.reload();
    for (const id of DATA_PANEL_IDS) {
        await expect(panelBox(page, id).getByTestId("data-testid Panel status error")).toBeVisible();
    }
    await expect(errorMarkers(page)).toHaveCount(DATA_PANEL_IDS.length);
    await expect(panelValue(page, PANEL.total)).toHaveText(noValueText(PANEL.total));
    await expect(panelValue(page, PANEL.exportAge)).toHaveText(noValueText(PANEL.exportAge));
    await expect(panelBox(page, PANEL.table)).toContainText(noValueText(PANEL.table));
    await expectRows(page, PANEL.table, []);

    await restore();
    await page.reload();
    await expectSelection(page, context, { globalIds: context.state.globalIds, contentIds: context.state.contentIds });
    await expect(errorMarkers(page)).toHaveCount(0);
}
