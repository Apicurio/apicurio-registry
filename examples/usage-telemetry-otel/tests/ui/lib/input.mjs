// The one input boundary of the browser tooling: it validates the run state or the processes scenario, resolves the Grafana
// endpoint, and seals what was validated. Plain ES module with no dependency, so it runs on Node 20 before any npm package is
// installed (the runner uses it before downloading anything) and is imported by the typed tests, which receive the typed
// projection below and never raw bytes. The accepted shell dashboard validator has another consumer contract and is not unified
// with this one.
import { createHash } from "node:crypto";

import { flowProblems, scenarioProblems } from "./plan.mjs";

/** @typedef {"global_id" | "content_id"} IdentifierType */
/** @typedef {{ clientId: string, identifierType: IdentifierType, identifierValue: string, operation: string, count: number }} SeriesRow */
/**
 * What one run of demo.sh recorded, reduced to what the tests expect. evaluationMs is the exact evaluation time in
 * milliseconds, converted from its decimal string without floating point arithmetic.
 * @typedef {{ runId: string, instanceId: string, fromMs: number, evaluationMs: number, evaluationTime: string, endpoint: string, datasourceUid: string, probeClient: string, series: SeriesRow[], globalIds: string[], contentIds: string[] }} RunState
 */
/**
 * Two real Registry processes that reuse the same typed identifiers, and the rows each must show.
 * @typedef {{ primary: string, other: string, fromMs: number, toMs: number, primaryRows: SeriesRow[], otherRows: SeriesRow[] }} ProcessScenario
 */
/**
 * Everything a test invocation needs, sealed by the runner: the validated input, the resolved endpoint and the directories
 * the invocation owns (set when sealed).
 * @typedef {{ format: 1, mode: "seeded" | "scenario" | "selftest", flows?: "tests" | "screenshots", grafanaUrl: string | null, grafanaUrlSource: string, datasourceUid: string | null, datasourceSource: string, input?: Record<string, unknown>, state?: RunState, scenario?: ProcessScenario, runDir?: string, reportDir?: string }} Bundle
 */

export const MAX_INPUT_BYTES = 4 * 1024 * 1024;
const GRAFANA_UID = /^[A-Za-z0-9_-]{1,40}$/;
const CANONICAL_ID = /^(0|[1-9][0-9]{0,14})$/;
const UNIX_SECONDS = /^[0-9]{10,11}(\.[0-9]{1,3})?$/;
const CONTROL = /[\u0000-\u001f\u007f]/;
const MAX_MS = 253402300799999;

/** Every problem found, so an operator fixes the input once. */
export class InputError extends Error {
    /** @param {string[]} problems */
    constructor(problems) {
        super(problems.join("; "));
        this.name = "InputError";
        this.problems = problems;
    }
}

/** @param {unknown} value @returns {value is Record<string, unknown>} */
const isObject = (value) => typeof value === "object" && value !== null && !Array.isArray(value);
/** @param {unknown} value @returns {value is string} */
const isText = (value) => typeof value === "string" && value.length > 0 && value.length <= 512 && !CONTROL.test(value);
/** A whole number JSON.parse did not have to round: anything past 2^53 - 1 is refused instead of silently changed.
 * @param {unknown} value @returns {value is number} */
const isSafeCount = (value) => typeof value === "number" && Number.isSafeInteger(value) && value >= 1;
/** @param {unknown} value @returns {value is number} */
const isTimeMs = (value) => typeof value === "number" && Number.isSafeInteger(value) && value >= 0 && value <= MAX_MS;

/**
 * "1795191182.943" to 1795191182943, exactly: the digits are combined as integers, never multiplied as floating point.
 * @param {string} text @returns {number | undefined}
 */
export function secondsToMs(text) {
    const match = UNIX_SECONDS.exec(text);
    if (match === null) {
        return undefined;
    }
    return Number(match[0].split(".")[0]) * 1000 + Number((match[0].split(".")[1] ?? "").padEnd(3, "0"));
}

/**
 * A URL that may be used and printed: http or https, a host, no user information (credentials in a URL end up in logs), no
 * query and no fragment. Returns its normal form without a trailing slash.
 * @param {string} text @param {string} what @param {string[]} problems @returns {string | undefined}
 */
export function publicUrl(text, what, problems) {
    if (typeof text !== "string" || text === "" || CONTROL.test(text) || /\s/.test(text)) {
        problems.push(`${what} must be a URL without spaces or control characters`);
        return undefined;
    }
    let url;
    try {
        url = new URL(text);
    } catch {
        problems.push(`${what} is not a URL`);
        return undefined;
    }
    if (url.protocol !== "http:" && url.protocol !== "https:") {
        problems.push(`${what} must be an http or https URL`);
    } else if (url.hostname === "") {
        problems.push(`${what} has no host`);
    } else if (url.username !== "" || url.password !== "") {
        problems.push(`${what} must not contain credentials (user information); give the account with GRAFANA_ADMIN_PASSWORD`);
    } else if (url.search !== "" || url.hash !== "") {
        problems.push(`${what} must not have a query or a fragment`);
    } else {
        return url.origin + (url.pathname === "/" ? "" : url.pathname.replace(/\/+$/, ""));
    }
    return undefined;
}

/** @typedef {(string | number)[]} Path object keys are strings and array positions are numbers, so no key can pass for a position */

/**
 * The number tokens of a JSON text, each with its path as key and position segments (["timeRange", "fromMs"], ["schemas", 0,
 * "globalId"]), as written. This walks text that JSON.parse has already accepted; it validates nothing and keeps no values, it only
 * reads each number as the characters it was written with, which is what JSON.parse cannot give back once it has rounded a token.
 * @param {string} text @returns {{ path: Path, token: string }[]}
 */
function numberTokens(text) {
    /** @type {{ path: Path, token: string }[]} */
    const found = [];
    let index = 0;
    const skipSpace = () => {
        while (index < text.length && /\s/.test(/** @type {string} */ (text[index]))) {
            index += 1;
        }
    };
    /** @returns {string} the decoded string at the current position */
    const readString = () => {
        let end = index + 1;
        while (text[end] !== '"') {
            end += text[end] === "\\" ? 2 : 1;
        }
        const decoded = /** @type {string} */ (JSON.parse(text.slice(index, end + 1)));
        index = end + 1;
        return decoded;
    };
    /** @param {Path} path */
    const readValue = (path) => {
        skipSpace();
        const char = text[index];
        if (char === "{" || char === "[") {
            const isObject = char === "{";
            index += 1;
            skipSpace();
            if (text[index] === (isObject ? "}" : "]")) {
                index += 1;
                return;
            }
            for (let position = 0; ; position += 1) {
                skipSpace();
                /** @type {Path} */
                let child = [...path, position];
                if (isObject) {
                    child = [...path, readString()];
                    skipSpace();
                    index += 1;
                }
                readValue(child);
                skipSpace();
                const separator = text[index];
                index += 1;
                if (separator !== ",") {
                    return;
                }
            }
        } else if (char === '"') {
            readString();
        } else {
            let end = index;
            while (end < text.length && !/[\s,\]}]/.test(/** @type {string} */ (text[end]))) {
                end += 1;
            }
            const token = text.slice(index, end);
            index = end;
            if (token.startsWith("-") || /^[0-9]/.test(token)) {
                found.push({ path, token });
            }
        }
    };
    readValue([]);
    return found;
}

/**
 * The tokens, among those the tooling reads as numbers, that this input contract does not accept. JSON.parse has already rounded a
 * token such as 3.0000000000000001 or 9007199254740993 by the time a value is checked, so the representation is judged on the
 * original text: a number the tooling consumes must be a plain non-negative integer that a double holds exactly. A fraction or an
 * exponent is refused even when it denotes a whole number. Numbers in fields the tooling does not read (demo.sh also records
 * evaluationTimeSeconds as a fractional number for other consumers) are not its business and are left alone.
 * @param {string} text @param {(path: Path) => boolean} consumed @returns {string[]}
 */
function numberTokenProblems(text, consumed) {
    /** @type {string[]} */
    const problems = [];
    const safe = BigInt(Number.MAX_SAFE_INTEGER);
    for (const { path, token } of numberTokens(text)) {
        if (problems.length >= 5 || !consumed(path)) {
            continue;
        }
        if (!/^(0|[1-9][0-9]*)$/.test(token)) {
            problems.push(`${describePath(path)} is ${token.slice(0, 40)}, which is not a plain whole number: fractions, exponents and negative numbers are not accepted there`);
        } else if (BigInt(token) > safe) {
            problems.push(`${describePath(path)} is ${token.slice(0, 40)}, larger than ${Number.MAX_SAFE_INTEGER}, which JSON.parse would round`);
        }
    }
    return problems;
}

/** @param {Path} path @returns {string} the path for a message only; never compared */
const describePath = (path) => path.map((segment) => (typeof segment === "number" ? `[${segment}]` : `.${segment}`)).join("");
/** @param {Path} path @param {...(string | number | null)} shape a string is that key, null is any array position @returns {boolean} */
const pathIs = (path, ...shape) => path.length === shape.length && shape.every((part, index) => (part === null ? typeof path[index] === "number" : path[index] === part));

/** The numeric fields of a run state that the tooling reads. @param {Path} path @returns {boolean} */
const isStateNumber = (path) =>
    pathIs(path, "schemaVersion") || pathIs(path, "timeRange", "fromMs") || pathIs(path, "schemas", null, "globalId") || pathIs(path, "schemas", null, "contentId") || pathIs(path, "expectedSeries", null, "count");

/**
 * The numeric fields of the processes scenario that the tooling reads.
 * @param {number} index the position of the processes scenario in the scenarios array @returns {(path: Path) => boolean}
 */
const scenarioNumbers = (index) => (path) =>
    pathIs(path, "schemaVersion") ||
    (path[0] === "scenarios" && path[1] === index && (pathIs(path, "scenarios", index, "fromMs") || pathIs(path, "scenarios", index, "toMs") || pathIs(path, "scenarios", index, "primaryRows", null, null) || pathIs(path, "scenarios", index, "otherRows", null, null)));

/**
 * @param {string} text @param {string} what
 * @param {"state" | "scenario" | "bundle"} kind which numeric fields are read from it (a bundle was already judged when it was prepared)
 * @returns {Record<string, unknown>}
 */
function parseObject(text, what, kind) {
    if (Buffer.byteLength(text, "utf8") > MAX_INPUT_BYTES) {
        throw new InputError([`${what} is larger than ${MAX_INPUT_BYTES} bytes`]);
    }
    let raw;
    try {
        raw = JSON.parse(text);
    } catch (error) {
        throw new InputError([`${what} is not valid JSON: ${/** @type {Error} */ (error).message}`]);
    }
    if (!isObject(raw)) {
        throw new InputError([`${what} must be exactly one JSON object`]);
    }
    if (kind !== "bundle") {
        const scenarios = Array.isArray(raw["scenarios"]) ? raw["scenarios"] : [];
        const processes = scenarios.findIndex((entry) => isObject(entry) && entry["kind"] === "processes");
        const tokens = numberTokenProblems(text, kind === "state" ? isStateNumber : scenarioNumbers(processes));
        if (tokens.length > 0) {
            throw new InputError(tokens.map((problem) => `${what}: ${problem}`));
        }
    }
    return raw;
}

/** @param {unknown} row @param {string} where @param {string[]} problems @returns {SeriesRow | undefined} */
function seriesRow(row, where, problems) {
    if (!isObject(row)) {
        problems.push(`${where} must be an object`);
        return undefined;
    }
    const { clientId, identifierType, identifierValue, operation, count } = row;
    if (!isText(clientId) || (identifierType !== "global_id" && identifierType !== "content_id") || typeof identifierValue !== "string" || !CANONICAL_ID.test(identifierValue) || !isText(operation) || !isSafeCount(count)) {
        problems.push(`${where} needs clientId, identifierType (global_id or content_id), a canonical numeric identifierValue, operation and a positive safe integer count`);
        return undefined;
    }
    return { clientId, identifierType, identifierValue, operation, count };
}

/** @param {SeriesRow[]} rows @param {string} where @param {string[]} problems */
function noDuplicates(rows, where, problems) {
    const seen = new Set();
    for (const row of rows) {
        const key = [row.clientId, row.identifierType, row.identifierValue, row.operation].join("\t");
        if (seen.has(key)) {
            problems.push(`${where} lists ${row.clientId} ${row.identifierType} ${row.identifierValue} ${row.operation} twice`);
        }
        seen.add(key);
    }
}

/**
 * The run state of one demo.sh run: version 2, exactly. Every cross-field rule is checked here, before anything is
 * downloaded or any browser starts.
 * @param {string} text @returns {RunState}
 */
export function parseRunState(text) {
    return runStateFrom(parseObject(text, "the run state", "state"));
}

/** @param {Record<string, unknown>} raw @returns {RunState} */
function runStateFrom(raw) {
    /** @type {string[]} */
    const problems = [];
    if (raw["schemaVersion"] !== 2) {
        problems.push(`schemaVersion must be 2, it is ${JSON.stringify(raw["schemaVersion"])}`);
    }
    if (!isText(raw["runId"])) {
        problems.push("runId must be a non-empty string");
    }
    const source = raw["source"];
    if (!isObject(source) || !isText(source["instanceId"])) {
        problems.push("source.instanceId must be a non-empty string");
    }
    const timeRange = raw["timeRange"];
    if (!isObject(timeRange) || !isTimeMs(timeRange["fromMs"])) {
        problems.push("timeRange.fromMs must be a safe whole number of milliseconds");
    }
    const evaluationTime = raw["evaluationTime"];
    const evaluationMs = typeof evaluationTime === "string" ? secondsToMs(evaluationTime) : undefined;
    if (evaluationMs === undefined) {
        problems.push("evaluationTime must be a string of Unix seconds with at most three decimals");
    }
    const endpoints = raw["endpoints"];
    /** @type {string | undefined} */
    let endpoint;
    let datasourceUid;
    if (!isObject(endpoints)) {
        problems.push("endpoints must be an object");
    } else {
        endpoint = publicUrl(/** @type {string} */ (endpoints["grafana"]), "endpoints.grafana", problems);
        const uid = endpoints["grafanaDatasourceUid"];
        if (typeof uid !== "string" || !GRAFANA_UID.test(uid)) {
            problems.push("endpoints.grafanaDatasourceUid must be a Grafana UID (letters, digits, - and _, at most 40)");
        } else {
            datasourceUid = uid;
        }
    }
    const clients = raw["clients"];
    if (!isObject(clients) || !isText(clients["probe"])) {
        problems.push("clients.probe must be a non-empty string");
    }
    /** @type {string[]} */
    const globalIds = [];
    /** @type {string[]} */
    const contentIds = [];
    const schemas = raw["schemas"];
    if (!Array.isArray(schemas) || schemas.length === 0 || schemas.length > 1000) {
        problems.push("schemas must be a non-empty array");
    } else {
        schemas.forEach((schema, index) => {
            if (!isObject(schema) || !isSafeCount(schema["globalId"]) || !isSafeCount(schema["contentId"])) {
                problems.push(`schemas[${index}] needs positive safe integer globalId and contentId`);
                return;
            }
            globalIds.push(String(schema["globalId"]));
            contentIds.push(String(schema["contentId"]));
        });
    }
    /** @type {SeriesRow[]} */
    const series = [];
    const expected = raw["expectedSeries"];
    if (!Array.isArray(expected) || expected.length === 0 || expected.length > 10000) {
        problems.push("expectedSeries must be a non-empty array");
    } else {
        expected.forEach((row, index) => {
            const parsed = seriesRow(row, `expectedSeries[${index}]`, problems);
            if (parsed !== undefined) {
                series.push(parsed);
            }
        });
        noDuplicates(series, "expectedSeries", problems);
    }
    if (problems.length === 0) {
        for (const row of series) {
            if (!(row.identifierType === "global_id" ? globalIds : contentIds).includes(row.identifierValue)) {
                problems.push(`expected series ${row.clientId} ${row.identifierType} ${row.identifierValue} is not an identifier of the run's schemas`);
            }
        }
        const fromMs = /** @type {number} */ (/** @type {Record<string, unknown>} */ (timeRange)["fromMs"]);
        if (evaluationMs !== undefined && fromMs >= evaluationMs) {
            problems.push("timeRange.fromMs must be before the evaluation time");
        }
    }
    if (problems.length > 0) {
        throw new InputError(problems);
    }
    return {
        runId: /** @type {string} */ (raw["runId"]),
        instanceId: /** @type {string} */ (/** @type {Record<string, unknown>} */ (source)["instanceId"]),
        fromMs: /** @type {number} */ (/** @type {Record<string, unknown>} */ (timeRange)["fromMs"]),
        evaluationMs: /** @type {number} */ (evaluationMs),
        evaluationTime: /** @type {string} */ (evaluationTime),
        endpoint: /** @type {string} */ (endpoint),
        datasourceUid: /** @type {string} */ (datasourceUid),
        probeClient: /** @type {string} */ (/** @type {Record<string, unknown>} */ (clients)["probe"]),
        series,
        globalIds: [...new Set(globalIds)],
        contentIds: [...new Set(contentIds)],
    };
}

/**
 * The scenarios.json of the lifecycle-owned scenario run: version 1, exactly, with exactly one "processes" scenario.
 * @param {string} text @returns {ProcessScenario}
 */
export function parseProcessScenario(text) {
    return scenarioFrom(parseObject(text, "the scenario file", "scenario"));
}

/** @param {Record<string, unknown>} raw @returns {ProcessScenario} */
function scenarioFrom(raw) {
    /** @type {string[]} */
    const problems = [];
    if (raw["schemaVersion"] !== 1) {
        problems.push(`schemaVersion must be 1, it is ${JSON.stringify(raw["schemaVersion"])}`);
    }
    const scenarios = raw["scenarios"];
    if (!Array.isArray(scenarios)) {
        throw new InputError([...problems, "scenarios must be an array"]);
    }
    const cases = scenarios.filter((entry) => isObject(entry) && entry["kind"] === "processes");
    if (cases.length !== 1) {
        throw new InputError([...problems, `the scenarios array must hold exactly one processes scenario, it holds ${cases.length}`]);
    }
    const scenario = /** @type {Record<string, unknown>} */ (cases[0]);
    const { primary, other, fromMs, toMs } = scenario;
    if (!isText(primary) || !isText(other) || primary === other) {
        problems.push("primary and other must be two different non-empty strings");
    }
    if (!isTimeMs(fromMs) || !isTimeMs(toMs)) {
        problems.push("fromMs and toMs must be safe whole numbers of milliseconds");
    } else if (fromMs >= toMs) {
        problems.push("fromMs must be before toMs");
    }
    /** @param {unknown} value @param {string} where @returns {SeriesRow[]} */
    const rows = (value, where) => {
        if (!Array.isArray(value) || value.length === 0 || value.length > 10000) {
            problems.push(`${where} must be a non-empty array`);
            return [];
        }
        /** @type {SeriesRow[]} */
        const parsed = [];
        value.forEach((row, index) => {
            if (!Array.isArray(row) || row.length !== 5) {
                problems.push(`${where}[${index}] must be [client, global_id or content_id, identifier, operation, count]`);
                return;
            }
            const result = seriesRow({ clientId: row[0], identifierType: row[1], identifierValue: row[2], operation: row[3], count: row[4] }, `${where}[${index}]`, problems);
            if (result !== undefined) {
                parsed.push(result);
            }
        });
        noDuplicates(parsed, where, problems);
        return parsed;
    };
    const primaryRows = rows(scenario["primaryRows"], "primaryRows");
    const otherRows = rows(scenario["otherRows"], "otherRows");
    if (problems.length > 0) {
        throw new InputError(problems);
    }
    return {
        primary: /** @type {string} */ (primary),
        other: /** @type {string} */ (other),
        fromMs: /** @type {number} */ (fromMs),
        toMs: /** @type {number} */ (toMs),
        primaryRows,
        otherRows,
    };
}

/**
 * Validates an input for the flows that will use it. A seeded run is prepared for one named set of flows ("tests" or "screenshots"),
 * and the selections those flows need are checked here, before any download or browser.
 * @param {{ mode: "seeded" | "scenario" | "selftest", flows?: "tests" | "screenshots", inputText?: string, urlOverride?: string, datasourceOverride?: string, defaultDatasourceUid?: string }} options
 * @returns {Bundle}
 */
export function prepareBundle(options) {
    const { mode, flows, inputText, urlOverride, datasourceOverride, defaultDatasourceUid } = options;
    /** @type {string[]} */
    const problems = [];
    /** @type {string | undefined} */
    let override;
    const urlGiven = urlOverride !== undefined && urlOverride !== "";
    if (urlGiven) {
        override = publicUrl(urlOverride, "the Grafana URL given with --grafana-url or GRAFANA_URL", problems);
    }
    /** @type {string | undefined} */
    let datasource;
    if (datasourceOverride !== undefined && datasourceOverride !== "") {
        if (GRAFANA_UID.test(datasourceOverride)) {
            datasource = datasourceOverride;
        } else {
            problems.push("GRAFANA_DATASOURCE_UID must be a Grafana UID (letters, digits, - and _, at most 40)");
        }
    }
    if (mode === "selftest") {
        if (problems.length > 0) {
            throw new InputError(problems);
        }
        return { format: 1, mode, grafanaUrl: null, grafanaUrlSource: "none", datasourceUid: null, datasourceSource: "none" };
    }
    if (inputText === undefined) {
        throw new InputError(["no input was given"]);
    }
    if (mode === "seeded") {
        if (flows !== "tests" && flows !== "screenshots") {
            throw new InputError(["a seeded input is prepared for named flows: flows must be tests or screenshots"]);
        }
        /** @type {Record<string, unknown> | undefined} */
        let input;
        /** @type {RunState | undefined} */
        let state;
        try {
            input = parseObject(inputText, "the run state", "state");
            state = runStateFrom(input);
        } catch (error) {
            if (!(error instanceof InputError)) {
                throw error;
            }
            problems.push(...error.problems);
        }
        if (state !== undefined) {
            problems.push(...flowProblems(state, flows));
        }
        if (problems.length > 0 || state === undefined) {
            throw new InputError(problems);
        }
        return {
            format: 1,
            mode,
            flows,
            grafanaUrl: override ?? state.endpoint,
            grafanaUrlSource: override === undefined ? "the run state" : "the caller",
            datasourceUid: datasource ?? state.datasourceUid,
            datasourceSource: datasource === undefined ? "the run state" : "the caller",
            input,
            state,
        };
    }
    /** @type {Record<string, unknown> | undefined} */
    let input;
    /** @type {ProcessScenario | undefined} */
    let scenario;
    try {
        input = parseObject(inputText, "the scenario file", "scenario");
        scenario = scenarioFrom(input);
    } catch (error) {
        if (!(error instanceof InputError)) {
            throw error;
        }
        problems.push(...error.problems);
    }
    if (!urlGiven) {
        problems.push("a scenario file does not record where its Grafana is: give --grafana-url (or GRAFANA_URL)");
    }
    if (scenario !== undefined) {
        problems.push(...scenarioProblems(scenario));
    }
    const fallback = datasource ?? defaultDatasourceUid;
    if (fallback === undefined || !GRAFANA_UID.test(fallback)) {
        problems.push("no valid datasource UID: set GRAFANA_DATASOURCE_UID");
    }
    if (problems.length > 0 || scenario === undefined) {
        throw new InputError(problems);
    }
    return {
        format: 1,
        mode: "scenario",
        grafanaUrl: /** @type {string} */ (override),
        grafanaUrlSource: "the caller",
        datasourceUid: /** @type {string} */ (fallback),
        datasourceSource: datasource === undefined ? "the example's default" : "the caller",
        input,
        scenario,
    };
}

/**
 * The one rule for a path the tooling is willing to use as a directory it writes to: absolute, normal (no empty, "." or ".." part, no
 * trailing slash) and free of control characters. The shell runner asks this function (the "path" subcommand) instead of keeping a
 * second rule of its own.
 * @param {unknown} path @returns {path is string}
 */
export const isCleanAbsolutePath = (path) => typeof path === "string" && path.startsWith("/") && !CONTROL.test(path) && !path.split("/").some((part) => part === ".." || part === ".") && !path.endsWith("/") && !path.includes("//");

/**
 * Validates a bundle from text (as prepared, or as sealed when sealed is true) and returns its typed projection. The same
 * rules apply on both sides of the seal: nothing is trusted because it came from the runner.
 * @param {string} text @param {boolean} sealed @returns {Bundle}
 */
export function parseBundle(text, sealed) {
    const raw = parseObject(text, "the bundle", "bundle");
    /** @type {string[]} */
    const problems = [];
    if (raw["format"] !== 1) {
        problems.push("format must be 1");
    }
    const mode = raw["mode"];
    if (mode !== "seeded" && mode !== "scenario" && mode !== "selftest") {
        problems.push("mode must be seeded, scenario or selftest");
        throw new InputError(problems);
    }
    /** @type {Bundle} */
    const bundle = {
        format: 1,
        mode,
        grafanaUrl: null,
        grafanaUrlSource: typeof raw["grafanaUrlSource"] === "string" ? raw["grafanaUrlSource"] : "",
        datasourceUid: null,
        datasourceSource: typeof raw["datasourceSource"] === "string" ? raw["datasourceSource"] : "",
    };
    if (mode !== "selftest") {
        const url = publicUrl(/** @type {string} */ (raw["grafanaUrl"]), "grafanaUrl", problems);
        bundle.grafanaUrl = url ?? null;
        if (typeof raw["datasourceUid"] !== "string" || !GRAFANA_UID.test(raw["datasourceUid"])) {
            problems.push("datasourceUid must be a Grafana UID");
        } else {
            bundle.datasourceUid = raw["datasourceUid"];
        }
        try {
            const input = raw["input"];
            if (!isObject(input)) {
                throw new InputError(["input must be the validated input object"]);
            }
            bundle.input = input;
            if (mode === "seeded") {
                const flows = raw["flows"];
                if (flows !== "tests" && flows !== "screenshots") {
                    throw new InputError(["flows must be tests or screenshots"]);
                }
                bundle.flows = flows;
                bundle.state = runStateFrom(input);
                problems.push(...flowProblems(bundle.state, flows));
            } else {
                bundle.scenario = scenarioFrom(input);
                problems.push(...scenarioProblems(bundle.scenario));
            }
        } catch (error) {
            if (error instanceof InputError) {
                problems.push(...error.problems);
            } else {
                throw error;
            }
        }
    }
    if (sealed) {
        if (typeof raw["runDir"] !== "string" || !isCleanAbsolutePath(raw["runDir"])) {
            problems.push("runDir must be a clean absolute path");
        } else {
            bundle.runDir = raw["runDir"];
        }
        if (typeof raw["reportDir"] !== "string" || !isCleanAbsolutePath(raw["reportDir"])) {
            problems.push("reportDir must be a clean absolute path");
        } else {
            bundle.reportDir = raw["reportDir"];
        }
        if (bundle.runDir !== undefined && bundle.reportDir !== undefined && (bundle.runDir === bundle.reportDir || bundle.runDir.startsWith(`${bundle.reportDir}/`) || bundle.reportDir.startsWith(`${bundle.runDir}/`))) {
            problems.push("runDir and reportDir must be separate directories");
        }
    }
    if (problems.length > 0) {
        throw new InputError(problems);
    }
    return bundle;
}

/**
 * Adds the directories this invocation owns to a prepared bundle and returns the exact text to write.
 * @param {string} preparedText @param {string} runDir @param {string} reportDir @returns {string}
 */
export function sealBundle(preparedText, runDir, reportDir) {
    const text = `${serializeBundle(parseBundle(preparedText, false), runDir, reportDir)}\n`;
    parseBundle(text, true);
    return text;
}

/**
 * The bytes of a bundle: the resolved facts and the validated input, never the projection (it is rebuilt by the parser).
 * @param {Bundle} bundle @param {string} [runDir] @param {string} [reportDir] @returns {string}
 */
export function serializeBundle(bundle, runDir, reportDir) {
    const { state: _state, scenario: _scenario, ...carried } = bundle;
    return JSON.stringify(runDir === undefined ? carried : { ...carried, runDir, reportDir });
}

/** @param {string | Uint8Array} bytes @returns {string} */
export const sha256Hex = (bytes) => createHash("sha256").update(bytes).digest("hex");
