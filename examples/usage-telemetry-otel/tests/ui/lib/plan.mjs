// What the browser flows need from a run, decided once from the validated input: the row selection every flow compares against, and
// the narrowing steps of the filter flow (which client, which global ID, which Content ID). Dependency-free like input.mjs, so the
// runner can refuse an input the flows cannot use before it downloads anything, and the typed flows consume the same answer.

/** @typedef {import("./input.mjs").RunState} RunState */
/** @typedef {import("./input.mjs").SeriesRow} SeriesRow */
/** @typedef {import("./input.mjs").IdentifierType} IdentifierType */
/** @typedef {{ clients?: string[], types?: IdentifierType[], globalIds: string[], contentIds: string[] }} Selection */
/**
 * The steps of the filter flow, in the order the flow takes them. contentTarget is the one Content ID the Content ID control is
 * narrowed to: when the chosen client has rows for two or more Content IDs it is one of them (contentBranch "rows"), otherwise it is
 * a Content ID of the run without a row there, which must empty the table (contentBranch "empty").
 * @typedef {{ client: string, chosenGlobal: string, otherGlobals: string[], contentTarget: string, otherContents: string[], contentBranch: "rows" | "empty" }} FilterPlan
 */

/**
 * The rows the dashboard shows for a selection: a row is listed when its client and type are selected and its identifier is one of
 * the selected identifiers OF ITS OWN TYPE, so global ID 46 and content ID 46 never stand in for each other.
 * @param {RunState} state @param {Selection} selection @returns {SeriesRow[]}
 */
export function selectRows(state, selection) {
    return state.series.filter((row) => {
        if (selection.clients !== undefined && !selection.clients.includes(row.clientId)) {
            return false;
        }
        if (selection.types !== undefined && !selection.types.includes(row.identifierType)) {
            return false;
        }
        const identifiers = row.identifierType === "global_id" ? selection.globalIds : selection.contentIds;
        return identifiers.includes(row.identifierValue);
    });
}

/** @param {SeriesRow[]} rows @returns {string[]} */
const sortedRowTexts = (rows) => rows.map((row) => [row.clientId, row.identifierType, row.identifierValue, row.operation, String(row.count)].join("\t")).sort();

/**
 * The narrowing steps of the filter flow for this run, or the reasons the run cannot support them. Nothing here asks for an
 * overlap between global and Content ID numbers: that case is conditional and reported separately.
 * @param {RunState} state @returns {{ plan?: FilterPlan, problems: string[] }}
 */
export function filterPlan(state) {
    const client = state.series.find((row) => row.identifierType === "global_id" && row.operation === "SERIALIZE")?.clientId;
    if (client === undefined) {
        return { problems: ["the filter flow needs a client with a global ID SERIALIZE series to filter by; this run has none"] };
    }
    /** @type {Selection} */
    const byClient = { clients: [client], globalIds: state.globalIds, contentIds: state.contentIds };
    /** @type {Selection} */
    const byType = { ...byClient, types: ["global_id"] };
    const globals = [...new Set(selectRows(state, byType).map((row) => row.identifierValue))];
    const [chosenGlobal] = globals;
    if (chosenGlobal === undefined || globals.length < 2) {
        return { problems: [`the filter flow needs client ${client} to have global ID series for two different global IDs so that narrowing to one is a visible change; it has ${globals.length}`] };
    }
    /** @type {Selection} */
    const byContentType = { ...byType, globalIds: [chosenGlobal], types: ["content_id"] };
    const before = selectRows(state, byContentType);
    const withRows = [...new Set(before.map((row) => row.identifierValue))];
    const narrowed = withRows.length >= 2 ? withRows.slice(0, 1) : state.contentIds.filter((id) => !withRows.includes(id)).slice(0, 1);
    const [contentTarget] = narrowed;
    if (contentTarget === undefined) {
        return { problems: [`the Content ID control step needs a Content ID of the run that narrows the selection of client ${client}: every Content ID of the run has a row there and there is only one`] };
    }
    const after = selectRows(state, { ...byContentType, contentIds: [contentTarget] });
    if (JSON.stringify(sortedRowTexts(after)) === JSON.stringify(sortedRowTexts(before))) {
        return { problems: [`narrowing the Content ID selection to ${contentTarget} would not change the rows of client ${client}`] };
    }
    return {
        plan: {
            client,
            chosenGlobal,
            otherGlobals: state.globalIds.filter((id) => id !== chosenGlobal),
            contentTarget,
            otherContents: state.contentIds.filter((id) => id !== contentTarget),
            contentBranch: withRows.length >= 2 ? "rows" : "empty",
        },
        problems: [],
    };
}

/**
 * What each mode consumes of a seeded run. "tests": the overview, the filter flow, the no-match test (the probe client must have a
 * series, so that Grafana offers it, and no Content ID series) and the failure test. "screenshots": the overview and the filter flow. Returns the problems, empty when usable.
 * @param {RunState} state @param {"tests" | "screenshots"} flows @returns {string[]}
 */
export function flowProblems(state, flows) {
    const problems = [...filterPlan(state).problems];
    if (flows === "tests") {
        if (!state.series.some((row) => row.clientId === state.probeClient)) {
            problems.push(`the probe client ${state.probeClient} has no series: Grafana offers only clients that have series, so the no-match test cannot select it`);
        } else if (selectRows(state, { clients: [state.probeClient], types: ["content_id"], globalIds: state.globalIds, contentIds: state.contentIds }).length !== 0) {
            problems.push(`the no-match test needs a probe client (${state.probeClient}) without Content ID series; this run has some`);
        }
    }
    return problems;
}

/**
 * What the processes scenario tests need: the process switch needs at least one typed identifier that both processes show, otherwise
 * switching processes proves nothing; the dependent client reload test narrows the second process to one client, so that process must
 * show at least two different clients, otherwise the narrowing is not a visible change. The one list of rules both the preparation of the
 * input and the sealed bundle use.
 * @param {{ primaryRows: SeriesRow[], otherRows: SeriesRow[] }} scenario @returns {string[]}
 */
export function scenarioProblems(scenario) {
    const problems = [];
    const reused = scenario.primaryRows.some((row) =>
        scenario.otherRows.some((other) => other.clientId === row.clientId && other.identifierType === row.identifierType && other.identifierValue === row.identifierValue && other.operation === row.operation),
    );
    if (!reused) {
        problems.push("the process switch test needs at least one typed identifier that both processes reuse; the two row lists share none");
    }
    const clients = new Set(scenario.otherRows.map((row) => row.clientId));
    if (clients.size < 2) {
        problems.push(`the dependent client reload test needs the second process to show at least two different clients, so that narrowing to one is a visible change; otherRows has ${clients.size}`);
    }
    return problems;
}
