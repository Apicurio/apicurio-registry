// Regressions of the input boundary and the tooling checks. Node's built-in runner, no npm package, no browser: they run on
// Node 20 before anything is installed (npm run unit, or node --test test/*.test.mjs).
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test, { after } from "node:test";
import { fileURLToPath } from "node:url";

import { InputError, parseBundle, parseProcessScenario, parseRunState, prepareBundle, publicUrl, sealBundle, secondsToMs, serializeBundle, sha256Hex } from "../lib/input.mjs";
import { installedProblems, reporterOverrides, summaryLines, verdictProblems } from "../lib/tooling.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const cli = join(here, "..", "lib", "cli.mjs");
const scratch = mkdtempSync(join(tmpdir(), "ui-input-test-"));
// only the scratch tree this suite created is removed; nothing a browser run retains
after(() => rmSync(scratch, { recursive: true, force: true }));

/** A valid run state; each case changes one thing of it. */
const state = () => ({
    schemaVersion: 2,
    runId: "20261120T161252Z-85dcbd",
    source: { label: "service_instance_id", instanceId: "3fdf1298-d99b-4832-9232-e4b5e3b118f0" },
    timeRange: { fromMs: 1795191142000, toMs: 1795191212000 },
    evaluationTime: "1795191182.943",
    evaluationTimeSeconds: 1795191182.943,
    endpoints: { grafana: "http://127.0.0.1:13000", grafanaDatasourceUid: "apicurio-usage-prometheus" },
    clients: { probe: "probe-20261120T161252Z-85dcbd" },
    schemas: [
        { globalId: 46, contentId: 46 },
        { globalId: 47, contentId: 47 },
        { globalId: 48, contentId: 46 },
    ],
    expectedSeries: [
        { clientId: "billing-service", identifierType: "global_id", identifierValue: "46", operation: "DESERIALIZE", count: 1 },
        { clientId: "billing-service", identifierType: "global_id", identifierValue: "47", operation: "UNKNOWN", count: 2 },
        { clientId: "orders-service", identifierType: "content_id", identifierValue: "46", operation: "DESERIALIZE", count: 2 },
        { clientId: "orders-service", identifierType: "global_id", identifierValue: "46", operation: "SERIALIZE", count: 3 },
        { clientId: "orders-service", identifierType: "global_id", identifierValue: "48", operation: "SERIALIZE", count: 1 },
        { clientId: "probe-20261120T161252Z-85dcbd", identifierType: "global_id", identifierValue: "46", operation: "UNKNOWN", count: 1 },
    ],
});
const scenario = () => ({
    schemaVersion: 1,
    scenarios: [
        { kind: "overflow", instance: "x" },
        {
            kind: "processes",
            primary: "8e3af4bd-dfbd-477b-a4c7-957f714891d1",
            other: "1bc81aed-07b5-4134-bbce-0a2b9ff0acf2",
            fromMs: 1795224000000,
            toMs: 1795224300000,
            primaryRows: [["c1", "global_id", "1", "SERIALIZE", 1]],
            otherRows: [["c1", "global_id", "1", "SERIALIZE", 150], ["c2", "global_id", "1", "DESERIALIZE", 75]],
        },
    ],
});
const processes = (/** @type {any} */ doc) => doc.scenarios.find((/** @type {any} */ s) => s.kind === "processes");
const problemsOf = (/** @type {() => unknown} */ run) => {
    try {
        run();
    } catch (error) {
        assert.ok(error instanceof InputError, `expected an InputError, got ${error}`);
        return error.problems.join(" | ");
    }
    return "";
};
const stateProblems = (/** @type {(s: any) => void} */ change) => {
    const doc = state();
    change(doc);
    return problemsOf(() => parseRunState(JSON.stringify(doc)));
};
const scenarioProblems = (/** @type {(s: any) => void} */ change) => {
    const doc = scenario();
    change(doc);
    return problemsOf(() => parseProcessScenario(JSON.stringify(doc)));
};

test("a valid run state is projected exactly, with the evaluation time converted without floating point", () => {
    const parsed = parseRunState(JSON.stringify(state()));
    assert.equal(parsed.evaluationMs, 1795191182943);
    assert.equal(parsed.fromMs, 1795191142000);
    assert.equal(parsed.endpoint, "http://127.0.0.1:13000");
    assert.deepEqual(parsed.globalIds, ["46", "47", "48"]);
    assert.deepEqual(parsed.contentIds, ["46", "47"], "content IDs are deduplicated: schema 48 shares content 46");
    assert.equal(parsed.series.length, 6);
    assert.equal(secondsToMs("1795191182.5"), 1795191182500);
    assert.equal(secondsToMs("1795191182"), 1795191182000);
    assert.equal(secondsToMs("1795191182.0004"), undefined);
});

test("run state: shape, version and required data", () => {
    assert.match(problemsOf(() => parseRunState("")), /not valid JSON/);
    assert.match(problemsOf(() => parseRunState("[]")), /exactly one JSON object/);
    assert.match(problemsOf(() => parseRunState("{not json")), /not valid JSON/);
    assert.match(problemsOf(() => parseRunState(`${JSON.stringify(state())}${JSON.stringify(state())}`)), /not valid JSON/, "two documents are not one");
    for (const version of [1, 3, "2", null, 2.5]) {
        assert.match(stateProblems((s) => (s.schemaVersion = version)), /schemaVersion must be 2|plain whole number/, `version ${JSON.stringify(version)}`);
    }
    assert.match(stateProblems((s) => delete s.schemaVersion), /schemaVersion must be 2/);
    assert.match(stateProblems((s) => delete s.runId), /runId/);
    assert.match(stateProblems((s) => delete s.source), /source\.instanceId/);
    assert.match(stateProblems((s) => delete s.timeRange), /timeRange\.fromMs/);
    assert.match(stateProblems((s) => delete s.evaluationTime), /evaluationTime/);
    assert.match(stateProblems((s) => delete s.endpoints), /endpoints must be an object/);
    assert.match(stateProblems((s) => delete s.clients), /clients\.probe/);
    assert.match(stateProblems((s) => delete s.schemas), /schemas must be a non-empty array/);
    assert.match(stateProblems((s) => (s.schemas = [])), /schemas must be a non-empty array/);
    assert.match(stateProblems((s) => delete s.expectedSeries), /expectedSeries must be a non-empty array/);
    assert.match(stateProblems((s) => (s.expectedSeries = [])), /expectedSeries must be a non-empty array/);
});

test("run state: times, numbers and identifiers are refused instead of silently rounded", () => {
    for (const bad of ["1795191182.9431", "1795191182,943", "soon", "179519118", "1795191182943000", "", " 1795191182.943", "1e9", "0x6b3d1f2e"]) {
        assert.match(stateProblems((s) => (s.evaluationTime = bad)), /evaluationTime/, `evaluationTime ${JSON.stringify(bad)}`);
    }
    assert.match(stateProblems((s) => (s.evaluationTime = 1795191182.943)), /evaluationTime|plain whole number/, "a number is not an exact decimal string");
    for (const bad of [1795191142000.5, -1, 2 ** 53, 1e300, "1795191142000", null]) {
        assert.match(stateProblems((s) => (s.timeRange.fromMs = bad)), /timeRange\.fromMs|plain whole number|larger than/, `fromMs ${JSON.stringify(bad)}`);
    }
    for (const bad of [0, -3, 1.5, 2 ** 53, "3"]) {
        assert.match(stateProblems((s) => (s.expectedSeries[0].count = bad)), /expectedSeries\[0\]|plain whole number|larger than/, `count ${JSON.stringify(bad)}`);
    }
    for (const bad of ["046", "4 6", "46.0", "-1", "", "9999999999999999", 46]) {
        assert.match(stateProblems((s) => (s.expectedSeries[0].identifierValue = bad)), /expectedSeries\[0\]/, `identifier ${JSON.stringify(bad)}`);
    }
    assert.match(stateProblems((s) => (s.schemas[0].globalId = 2 ** 53)), /larger than/);
    assert.match(stateProblems((s) => (s.schemas[0].contentId = 0)), /schemas\[0\]/);
    assert.match(stateProblems((s) => (s.expectedSeries[0].identifierType = "id")), /expectedSeries\[0\]/);
    assert.match(stateProblems((s) => (s.expectedSeries[0].operation = "")), /expectedSeries\[0\]/);
    assert.match(stateProblems((s) => (s.expectedSeries[0].clientId = "a\nb")), /expectedSeries\[0\]/);
});

test("run state: the cross-field rules that used to be checked late or not at all", () => {
    assert.match(stateProblems((s) => (s.timeRange.fromMs = 1795191182943)), /fromMs must be before the evaluation time/, "equal");
    assert.match(stateProblems((s) => (s.timeRange.fromMs = 1795191183000)), /fromMs must be before the evaluation time/, "reversed");
    assert.match(stateProblems((s) => (s.expectedSeries[0].identifierValue = "99")), /not an identifier of the run's schemas/);
    // 47 is a global ID and a content ID of different schemas; 48 is a global ID only: the typed sets are not interchangeable
    assert.match(stateProblems((s) => (s.expectedSeries[1] = { clientId: "orders-service", identifierType: "content_id", identifierValue: "48", operation: "DESERIALIZE", count: 2 })), /content_id 48 is not an identifier/);
    assert.match(stateProblems((s) => s.expectedSeries.push({ ...s.expectedSeries[0] })), /twice/);
});

test("endpoints: only an http or https URL without credentials, query or fragment", () => {
    const check = (/** @type {string} */ url) => {
        const problems = /** @type {string[]} */ ([]);
        const result = publicUrl(url, "the URL", problems);
        return { result, problems: problems.join(" | ") };
    };
    assert.equal(check("http://127.0.0.1:13000").result, "http://127.0.0.1:13000");
    assert.equal(check("https://grafana.example.org/grafana/").result, "https://grafana.example.org/grafana");
    for (const bad of ["ftp://x", "javascript:alert(1)", "file:///etc/passwd", "grafana", "", "http://", "http://a b", "http://x/\u0001"]) {
        assert.ok(check(bad).problems !== "", `refused: ${JSON.stringify(bad)}`);
    }
    const credentials = check("http://admin:secret@127.0.0.1:3000");
    assert.match(credentials.problems, /credentials/);
    assert.ok(!credentials.problems.includes("secret"), "the diagnostic never repeats the credential");
    assert.match(check("http://user@127.0.0.1:3000").problems, /credentials/);
    assert.match(check("http://127.0.0.1:3000/?a=1").problems, /query/);
    assert.match(check("http://127.0.0.1:3000/#x").problems, /fragment/);
    assert.match(stateProblems((s) => (s.endpoints.grafana = "http://u:p@127.0.0.1:1")), /endpoints\.grafana must not contain credentials/);
    assert.match(stateProblems((s) => (s.endpoints.grafanaDatasourceUid = "a b")), /grafanaDatasourceUid/);
    assert.match(stateProblems((s) => (s.endpoints.grafanaDatasourceUid = "x".repeat(41))), /grafanaDatasourceUid/);
});

test("scenario: version 1, exactly one processes case, a forward interval, typed rows", () => {
    const parsed = parseProcessScenario(JSON.stringify(scenario()));
    assert.equal(parsed.primaryRows[0]?.count, 1);
    assert.equal(parsed.otherRows[0]?.count, 150);
    for (const version of [999, 2, 0, "1", null]) {
        assert.match(scenarioProblems((s) => (s.schemaVersion = version)), /schemaVersion must be 1/, `version ${JSON.stringify(version)}`);
    }
    assert.match(scenarioProblems((s) => delete s.schemaVersion), /schemaVersion must be 1/);
    assert.match(scenarioProblems((s) => (s.scenarios = [])), /exactly one processes scenario, it holds 0/);
    assert.match(scenarioProblems((s) => s.scenarios.push({ ...processes(s) })), /exactly one processes scenario, it holds 2/);
    assert.match(scenarioProblems((s) => (s.scenarios = "x")), /scenarios must be an array/);
    assert.match(scenarioProblems((s) => ([processes(s).fromMs, processes(s).toMs] = [processes(s).toMs, processes(s).fromMs])), /fromMs must be before toMs/, "reversed");
    assert.match(scenarioProblems((s) => (processes(s).toMs = processes(s).fromMs)), /fromMs must be before toMs/, "empty");
    for (const bad of [1.5, -1, 2 ** 53, "1795224000000", null]) {
        assert.match(scenarioProblems((s) => (processes(s).fromMs = bad)), /safe whole numbers|plain whole number|larger than/, `fromMs ${JSON.stringify(bad)}`);
    }
    assert.match(scenarioProblems((s) => (processes(s).other = processes(s).primary)), /two different non-empty strings/);
    assert.match(scenarioProblems((s) => (processes(s).primary = "")), /two different non-empty strings/);
    assert.match(scenarioProblems((s) => (processes(s).primaryRows = [])), /primaryRows must be a non-empty array/);
    assert.match(scenarioProblems((s) => delete processes(s).otherRows), /otherRows must be a non-empty array/);
    assert.match(scenarioProblems((s) => (processes(s).primaryRows = [["c1", "global_id", "1", "SERIALIZE"]])), /primaryRows\[0\] must be/);
    assert.match(scenarioProblems((s) => (processes(s).primaryRows = [["c1", "global_id", "1", "SERIALIZE", 0]])), /primaryRows\[0\]/);
    assert.match(scenarioProblems((s) => (processes(s).primaryRows = [["c1", "other_id", "1", "SERIALIZE", 1]])), /primaryRows\[0\]/);
    assert.match(scenarioProblems((s) => (processes(s).primaryRows = [["c1", "global_id", "1", "SERIALIZE", 1], ["c1", "global_id", "1", "SERIALIZE", 2]])), /twice/);
});

test("the endpoint and datasource come only from the chosen input and the documented overrides", () => {
    const seeded = prepareBundle({ mode: "seeded", flows: "tests", inputText: JSON.stringify(state()) });
    assert.equal(seeded.grafanaUrl, "http://127.0.0.1:13000");
    assert.equal(seeded.grafanaUrlSource, "the run state");
    assert.equal(seeded.datasourceUid, "apicurio-usage-prometheus");
    const overridden = prepareBundle({ mode: "seeded", flows: "tests", inputText: JSON.stringify(state()), urlOverride: "https://grafana.example.org", datasourceOverride: "ops-prom-7" });
    assert.equal(overridden.grafanaUrl, "https://grafana.example.org");
    assert.equal(overridden.grafanaUrlSource, "the caller");
    assert.equal(overridden.datasourceUid, "ops-prom-7");
    assert.match(problemsOf(() => prepareBundle({ mode: "seeded", flows: "tests", inputText: JSON.stringify(state()), urlOverride: "http://admin:pw@127.0.0.1:1" })), /credentials/);
    assert.match(problemsOf(() => prepareBundle({ mode: "seeded", flows: "tests", inputText: JSON.stringify(state()), datasourceOverride: "a b" })), /GRAFANA_DATASOURCE_UID/);
    // a bad override and a bad input are both reported
    const both = problemsOf(() => prepareBundle({ mode: "seeded", flows: "tests", inputText: JSON.stringify({ ...state(), schemaVersion: 1 }), urlOverride: "ftp://x" }));
    assert.match(both, /schemaVersion/);
    assert.match(both, /http or https/);

    const scenarioBundle = prepareBundle({ mode: "scenario", inputText: JSON.stringify(scenario()), urlOverride: "http://127.0.0.1:43000", defaultDatasourceUid: "apicurio-usage-prometheus" });
    assert.equal(scenarioBundle.datasourceUid, "apicurio-usage-prometheus");
    assert.equal(scenarioBundle.datasourceSource, "the example's default");
    assert.match(problemsOf(() => prepareBundle({ mode: "scenario", inputText: JSON.stringify(scenario()), defaultDatasourceUid: "apicurio-usage-prometheus" })), /does not record where its Grafana is/);
    assert.match(problemsOf(() => prepareBundle({ mode: "scenario", inputText: JSON.stringify(scenario()), urlOverride: "http://127.0.0.1:43000" })), /no valid datasource UID/);
    assert.match(problemsOf(() => prepareBundle({ mode: "scenario", inputText: JSON.stringify({ ...scenario(), schemaVersion: 999 }), urlOverride: "http://127.0.0.1:43000", defaultDatasourceUid: "d" })), /schemaVersion must be 1/);
    const selftest = prepareBundle({ mode: "selftest" });
    assert.equal(selftest.grafanaUrl, null);
});

test("a sealed bundle is validated by the same parser on the other side and carries only what was validated", () => {
    const prepared = prepareBundle({ mode: "seeded", flows: "tests", inputText: JSON.stringify(state()), datasourceOverride: "ops-prom-7" });
    const preparedText = serializeBundle(prepared);
    assert.ok(!preparedText.includes('"state"'), "the projection is not serialized, only the validated input");
    const sealedText = sealBundle(preparedText, "/work/test-results/run-1", "/work/playwright-report/run-1");
    const sealed = parseBundle(sealedText, true);
    assert.equal(sealed.mode, "seeded");
    assert.equal(sealed.state?.evaluationMs, 1795191182943);
    assert.equal(sealed.datasourceUid, "ops-prom-7");
    assert.equal(sealed.runDir, "/work/test-results/run-1");
    for (const [runDir, reportDir, what] of [
        ["relative/run", "/work/report", "relative"],
        ["/work/../run", "/work/report", "dot-dot"],
        ["/work/run/", "/work/report", "trailing slash"],
        ["/work//run", "/work/report", "double slash"],
        ["/work/run", "/work/run", "same"],
        ["/work/run", "/work/run/report", "nested"],
        ["/work/run/report", "/work/run", "nested the other way"],
    ]) {
        assert.throws(() => sealBundle(preparedText, /** @type {string} */ (runDir), /** @type {string} */ (reportDir)), InputError, what);
    }
    // a hand edited bundle is validated again: a state that would not pass as input does not pass inside a bundle
    const tampered = JSON.parse(sealedText);
    tampered.input.timeRange.fromMs = tampered.input.timeRange.fromMs + 10_000_000_000;
    assert.match(problemsOf(() => parseBundle(JSON.stringify(tampered), true)), /fromMs must be before/);
    tampered.input = undefined;
    assert.match(problemsOf(() => parseBundle(JSON.stringify(tampered), true)), /input must be/);
    assert.throws(() => parseBundle(JSON.stringify({ format: 2, mode: "seeded" }), false), InputError);
    assert.equal(sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

/** @param {string[]} args @param {string} [input] */
const runCli = (args, input) => spawnSync(process.execPath, [cli, ...args], { input, encoding: "utf8" });

test("the command line reads the input once, seals that snapshot and refuses bad input with status 2 before writing anything", () => {
    const dir = mkdtempSync(join(scratch, "cli-"));
    const source = join(dir, "run-state.json");
    writeFileSync(source, JSON.stringify(state()));
    const prepared = runCli(["prepare", "--mode", "seeded", "--flows", "tests", "--input", source]);
    assert.equal(prepared.status, 0, prepared.stderr);
    // the source changes after it was read: the sealed bundle is the snapshot that was validated
    writeFileSync(source, "{}");
    const runDir = join(dir, "run");
    mkdirSync(runDir);
    const sealed = runCli(["seal", "--run-dir", runDir, "--report-dir", join(dir, "report")], prepared.stdout);
    assert.equal(sealed.status, 0, sealed.stderr);
    const bundlePath = join(runDir, "input.sealed.json");
    assert.equal(sealed.stdout.trim(), sha256Hex(readFileSync(bundlePath)), "the printed hash is the hash of the bytes written");
    assert.equal(statSync(bundlePath).mode & 0o777, 0o444, "read only");
    assert.equal(parseBundle(readFileSync(bundlePath, "utf8"), true).state?.runId, "20261120T161252Z-85dcbd");
    assert.notEqual(runCli(["seal", "--run-dir", runDir, "--report-dir", join(dir, "report")], prepared.stdout).status, 0, "an existing snapshot is never overwritten");

    const before = existsSync(join(dir, "other"));
    for (const [what, args] of /** @type {[string, string[]][]} */ ([
        ["missing file", ["prepare", "--mode", "seeded", "--flows", "tests", "--input", join(dir, "absent.json")]],
        ["empty file", ["prepare", "--mode", "seeded", "--flows", "tests", "--input", (writeFileSync(join(dir, "empty.json"), ""), join(dir, "empty.json"))]],
        ["scenario with version 999", ["prepare", "--mode", "scenario", "--input", (writeFileSync(join(dir, "v999.json"), JSON.stringify({ ...scenario(), schemaVersion: 999 })), join(dir, "v999.json")), "--grafana-url", "http://127.0.0.1:1", "--default-datasource-uid", "d"]],
        ["reversed scenario interval", ["prepare", "--mode", "scenario", "--input", (writeFileSync(join(dir, "rev.json"), JSON.stringify((() => { const s = scenario(); [processes(s).fromMs, processes(s).toMs] = [processes(s).toMs, processes(s).fromMs]; return s; })())), join(dir, "rev.json")), "--grafana-url", "http://127.0.0.1:1", "--default-datasource-uid", "d"]],
        ["bad mode", ["prepare", "--mode", "x"]],
        ["unknown command", ["bogus"]],
    ])) {
        const result = runCli(args);
        assert.equal(result.status, 2, `${what}: ${result.stderr}`);
        assert.equal(result.stdout, "", `${what}: nothing on stdout`);
        assert.ok(result.stderr.trim() !== "", `${what}: a diagnostic`);
    }
    assert.equal(before, existsSync(join(dir, "other")));
});

test("installed packages: every locked package at the locked version, optional ones may be absent", () => {
    const dir = mkdtempSync(join(scratch, "installed-"));
    const lock = { lockfileVersion: 3, packages: { "": {}, "node_modules/@playwright/test": { version: "1.65.1" }, "node_modules/playwright": { version: "1.65.1" }, "node_modules/playwright-core": { version: "1.65.1" }, "node_modules/fsevents": { version: "2.3.2", optional: true } } };
    writeFileSync(join(dir, "package-lock.json"), JSON.stringify(lock));
    const install = (/** @type {string} */ name, /** @type {string} */ version) => {
        mkdirSync(join(dir, "node_modules", name), { recursive: true });
        writeFileSync(join(dir, "node_modules", name, "package.json"), JSON.stringify({ name, version }));
    };
    assert.match(installedProblems(dir).join("|"), /@playwright\/test is not installed/);
    for (const name of ["@playwright/test", "playwright", "playwright-core"]) {
        install(name, "1.65.1");
    }
    assert.match(installedProblems(dir).join("|"), /\.bin\/playwright is missing/);
    mkdirSync(join(dir, "node_modules", ".bin"));
    writeFileSync(join(dir, "node_modules", ".bin", "playwright"), "#!/bin/sh\n");
    chmodSync(join(dir, "node_modules", ".bin", "playwright"), 0o755);
    assert.deepEqual(installedProblems(dir), [], "whole, with the optional package absent");
    install("playwright-core", "1.64.1");
    assert.match(installedProblems(dir).join("|"), /playwright-core is 1\.64\.1, locked 1\.65\.1/, "the wrapper is right but the tool actually executed is not");
    install("playwright-core", "1.65.1");
    install("playwright", "1.50.0");
    assert.match(installedProblems(dir).join("|"), /node_modules\/playwright is 1\.50\.0/);
    assert.match(installedProblems(join(dir, "nowhere")).join("|"), /does not exist/);
    writeFileSync(join(dir, "node_modules", "playwright", "package.json"), "{broken");
    assert.match(installedProblems(dir).join("|"), /cannot be read/);
});

test("reporter environment: every variable that can redirect, add or open a reporter is named, never its value", () => {
    const hostile = {
        PLAYWRIGHT_HTML_OUTPUT_DIR: "/elsewhere/a",
        PLAYWRIGHT_HTML_REPORT: "/elsewhere/b",
        PLAYWRIGHT_HTML_OPEN: "always",
        PW_TEST_HTML_REPORT_OPEN: "on-failure",
        PLAYWRIGHT_HTML_HOST: "0.0.0.0",
        PLAYWRIGHT_HTML_PORT: "9323",
        PLAYWRIGHT_HTML_ATTACHMENTS_BASE_URL: "https://x/",
        PLAYWRIGHT_HTML_TITLE: "t",
        PLAYWRIGHT_JSON_OUTPUT_FILE: "/elsewhere/c.json",
        PLAYWRIGHT_JSON_OUTPUT_DIR: "/elsewhere/d",
        PLAYWRIGHT_JSON_OUTPUT_NAME: "e.json",
        PLAYWRIGHT_JUNIT_OUTPUT_FILE: "/elsewhere/f.xml",
        PLAYWRIGHT_BLOB_OUTPUT_DIR: "/elsewhere/g",
        PW_TEST_REPORTER: "html",
        PLAYWRIGHT_LAST_RUN_OUTPUT_FILE: "/elsewhere/h.json",
    };
    const found = reporterOverrides({ ...hostile, PLAYWRIGHT_BROWSERS_PATH: "/owned", HOME: "/h", PATH: "/bin" });
    assert.deepEqual(found, Object.keys(hostile).sort());
    assert.ok(!JSON.stringify(found).includes("elsewhere"), "names only");
    assert.deepEqual(reporterOverrides({ PLAYWRIGHT_BROWSERS_PATH: "/owned", PW_TEST_SOURCE_TRANSFORM: "1", PLAYWRIGHT_DOWNLOAD_HOST: "https://mirror" }), [], "downloads and Playwright's own worker variables are not reporter controls");
});

test("diagnostics survive a missing report or output directory and list only what exists", () => {
    const dir = mkdtempSync(join(scratch, "summary-"));
    const lines = summaryLines({ runDir: join(dir, "run-absent"), reportDir: join(dir, "report-absent"), label: "x" });
    assert.match(lines.join("\n"), /no results\.json/);
    assert.match(lines.join("\n"), /report:      none was written/);
    assert.ok(!lines.join("\n").includes("screenshot:"));
    assert.ok(!lines.join("\n").includes("sensitive"), "no artifact, no warning");
    assert.deepEqual(verdictProblems({ runDir: join(dir, "run-absent"), screenshots: [] }), ["Playwright exited 0 but wrote no readable results.json"]);
    const run = join(dir, "run");
    mkdirSync(join(run, "screenshots"), { recursive: true });
    mkdirSync(join(run, "pw", "t1"), { recursive: true });
    writeFileSync(join(run, "results.json"), JSON.stringify({ stats: { expected: 2, unexpected: 1, skipped: 0, flaky: 0 }, suites: [{ specs: [{ title: "filters", tests: [{ annotations: [{ type: "coverage", description: "same-number isolation ran for 46" }] }] }] }] }));
    writeFileSync(join(run, "pw", "t1", "trace.zip"), "x");
    writeFileSync(join(run, "pw", "t1", "test-failed-1.png"), "x");
    const text = summaryLines({ runDir: run, reportDir: join(dir, "report"), label: "x" }).join("\n");
    assert.match(text, /tests: 2 passed, 1 failed, 0 skipped, 0 flaky/);
    assert.match(text, /coverage: filters: same-number isolation ran for 46/);
    assert.match(text, /trace:/);
    assert.match(text, /failure screenshot:/);
    assert.match(text, /note: real operator reports, screenshots and traces can show.*vetted synthetic/s);
    assert.match(text, /note: a trace starts only after the sign-in was accepted.*no trace/s);
    assert.match(verdictProblems({ runDir: run, screenshots: [] }).join("|"), /not every test ran and passed/);
    writeFileSync(join(run, "results.json"), JSON.stringify({ stats: { expected: 2, unexpected: 0, skipped: 0, flaky: 0 } }));
    assert.deepEqual(verdictProblems({ runDir: run, screenshots: [] }), []);
    assert.match(verdictProblems({ runDir: run, screenshots: ["overview"] }).join("|"), /overview\.png was not written as a PNG/);
    writeFileSync(join(run, "screenshots", "overview.png"), Buffer.from("89504e470d0a1a0a00", "hex"));
    assert.deepEqual(verdictProblems({ runDir: run, screenshots: ["overview"] }), []);
    for (const stats of [{ expected: 0, unexpected: 0, skipped: 0, flaky: 0 }, { expected: 1, unexpected: 0, skipped: 1, flaky: 0 }, { expected: 1, unexpected: 0, skipped: 0, flaky: 1 }]) {
        writeFileSync(join(run, "results.json"), JSON.stringify({ stats }));
        assert.notDeepEqual(verdictProblems({ runDir: run, screenshots: [] }), [], JSON.stringify(stats));
    }
});
