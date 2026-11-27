// Regressions of the boundary correction after the review of the first correction: raw numeric tokens, the selections the browser
// flows need, the Grafana base path in route matching, the diagnostic command line and the artifact notes. Node's built-in
// runner, no npm package, no browser (npm run unit, or node --test test/*.test.mjs).
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test, { after } from "node:test";
import { fileURLToPath } from "node:url";

import { InputError, parseBundle, parseProcessScenario, parseRunState, prepareBundle, sha256Hex } from "../lib/input.mjs";
import { summaryLines } from "../lib/tooling.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const cli = join(here, "..", "lib", "cli.mjs");
const scratch = mkdtempSync(join(tmpdir(), "ui-boundary-test-"));
after(() => rmSync(scratch, { recursive: true, force: true }));

/** The six series of a real demo run (global 46, 47 and 48, content 46 and 47) as the exact text demo.sh writes. */
const RAW_STATE = `{
  "schemaVersion": 2,
  "runId": "20261120T161252Z-85dcbd",
  "source": {"label": "service_instance_id", "instanceId": "3fdf1298-d99b-4832-9232-e4b5e3b118f0"},
  "timeRange": {"fromMs": 1795191142000, "toMs": 1795191212000},
  "evaluationTime": "1795191182.943",
  "evaluationTimeSeconds": 1795191182.943,
  "settledAtEpochSeconds": 1.795191182943e9,
  "endpoints": {"grafana": "http://127.0.0.1:13000", "grafanaDatasourceUid": "apicurio-usage-prometheus"},
  "clients": {"stable": ["orders-service", "billing-service"], "probe": "probe-20261120T161252Z-85dcbd"},
  "schemas": [{"globalId": 46, "contentId": 46}, {"globalId": 47, "contentId": 47}, {"globalId": 48, "contentId": 46}],
  "expectedSeries": [
    {"clientId": "billing-service", "identifierType": "global_id", "identifierValue": "46", "operation": "DESERIALIZE", "count": 1},
    {"clientId": "billing-service", "identifierType": "global_id", "identifierValue": "47", "operation": "UNKNOWN", "count": 2},
    {"clientId": "orders-service", "identifierType": "content_id", "identifierValue": "46", "operation": "DESERIALIZE", "count": 2},
    {"clientId": "orders-service", "identifierType": "global_id", "identifierValue": "46", "operation": "SERIALIZE", "count": 3},
    {"clientId": "orders-service", "identifierType": "global_id", "identifierValue": "48", "operation": "SERIALIZE", "count": 1},
    {"clientId": "probe-20261120T161252Z-85dcbd", "identifierType": "global_id", "identifierValue": "46", "operation": "UNKNOWN", "count": 1}
  ]
}`;
const RAW_SCENARIO = `{"schemaVersion": 1, "scenarios": [{"kind": "processes", "primary": "p1", "other": "p2", "fromMs": 1795224000000, "toMs": 1795224300000,
  "primaryRows": [["c1", "global_id", "1", "SERIALIZE", 1]], "otherRows": [["c1", "global_id", "1", "SERIALIZE", 150], ["c2", "global_id", "1", "DESERIALIZE", 75]]}]}`;

/** Replaces one literal occurrence in the raw text, so the token the parser sees is exactly the one written here. */
const raw = (text, from, to) => {
    assert.ok(text.includes(from), `the fixture has no ${from}`);
    return text.replace(from, to);
};
const problemsOf = (run) => {
    try {
        run();
    } catch (error) {
        assert.ok(error instanceof InputError, `expected an InputError, got ${error}`);
        return error.problems.join(" | ");
    }
    return "";
};
const seeded = (inputText, flows = "tests") => prepareBundle({ mode: "seeded", inputText, flows });

test("raw numeric tokens: only plain safe integers are accepted, and the token itself is judged, not the parsed number", () => {
    const state = (from, to) => raw(RAW_STATE, from, to);
    const refused = [
        ['"count": 3}', '"count": 3.0000000000000001}', "a fraction that JSON.parse rounds to 3"],
        ['"count": 3}', '"count": 3.0}', "a fraction written as an integer"],
        ['"count": 3}', '"count": 3e0}', "an exponent"],
        ['"count": 3}', '"count": 3E0}', "an exponent in capitals"],
        ['"count": 3}', '"count": 0.3e1}', "an exponent that makes 3"],
        ['"count": 3}', '"count": 9007199254740993}', "an integer past 2^53 that JSON.parse rounds"],
        ['"count": 3}', '"count": 1e400}', "a value past the double range"],
        ['"count": 3}', '"count": -3}', "a negative number"],
        ['"fromMs": 1795191142000,', '"fromMs": 1795191142000.0,', "a fractional time"],
        ['"fromMs": 1795191142000,', '"fromMs": 1.795191142e12,', "an exponent time"],
        ['"globalId": 46, "contentId": 46}', '"globalId": 46.0, "contentId": 46}', "a fractional global ID"],
        ['"schemaVersion": 2,', '"schemaVersion": 2.0,', "a version written as 2.0"],
    ];
    for (const [from, to, why] of refused) {
        const text = state(from, to);
        assert.notEqual(problemsOf(() => parseRunState(text)), "", `parseRunState must refuse ${why}`);
        assert.notEqual(problemsOf(() => seeded(text)), "", `prepare must refuse ${why}`);
    }
    // numbers inside strings are strings, never rewritten or judged
    const strings = state('"runId": "20261120T161252Z-85dcbd"', '"runId": "r-3.0000000000000001e3"').replace(/"operation": "UNKNOWN"/, '"operation": "U-1.5e3"');
    assert.equal(parseRunState(strings).runId, "r-3.0000000000000001e3");
    assert.equal(parseRunState(RAW_STATE).series.find((row) => row.clientId === "orders-service" && row.identifierValue === "46" && row.operation === "SERIALIZE")?.count, 3);
    // the same rule for the scenario file
    for (const token of ["150.0", "1.5e2", "150.00000000000000001", "-150"]) {
        const text = raw(RAW_SCENARIO, '"SERIALIZE", 150]', `"SERIALIZE", ${token}]`);
        assert.notEqual(problemsOf(() => parseProcessScenario(text)), "", `the scenario must refuse the count ${token}`);
    }
    assert.equal(parseProcessScenario(RAW_SCENARIO).otherRows[0].count, 150);
    // fields the tooling does not read keep whatever representation their writer chose (demo.sh records evaluationTimeSeconds as a
    // fractional number for other consumers, and other scenario kinds have times of their own)
    assert.equal(parseRunState(RAW_STATE).evaluationMs, 1795191182943);
    const otherKind = RAW_SCENARIO.replace('{"kind": "processes"', '{"kind": "rates", "fromMs": 1.5e12, "count": 0.5}, {"kind": "processes"');
    assert.equal(parseProcessScenario(otherKind).primary, "p1");
    assert.notEqual(problemsOf(() => parseProcessScenario(raw(otherKind, '"toMs": 1795224300000', '"toMs": 1795224300000.5'))), "", "the processes scenario itself is strict");
});

test("the selections the browser flows need are decided before any effect, for the mode that will use them", () => {
    const withoutRows = (keep) => {
        const doc = JSON.parse(RAW_STATE);
        doc.expectedSeries = doc.expectedSeries.filter(keep);
        return JSON.stringify(doc);
    };
    assert.equal(seeded(RAW_STATE, "tests").mode, "seeded", "a real demo run has everything the flows need");
    assert.equal(seeded(RAW_STATE, "screenshots").mode, "seeded");
    const oneRow = withoutRows((row) => row.clientId === "orders-service" && row.operation === "SERIALIZE" && row.identifierValue === "46");
    assert.doesNotThrow(() => parseRunState(oneRow), "the authoritative parser still accepts it: it is a valid run state");
    for (const flows of ["tests", "screenshots"]) {
        assert.match(problemsOf(() => seeded(oneRow, flows)), /global ID/, `a one-row state cannot run the ${flows} flows`);
    }
    const noSerialize = withoutRows((row) => row.operation !== "SERIALIZE");
    assert.match(problemsOf(() => seeded(noSerialize, "tests")), /SERIALIZE/);
    // the content ID step needs a content ID that is not among the client's rows, or two that are
    const everyContent = JSON.parse(RAW_STATE);
    everyContent.schemas = [{ globalId: 46, contentId: 46 }, { globalId: 48, contentId: 46 }];
    everyContent.expectedSeries = everyContent.expectedSeries.filter((row) => row.identifierValue !== "47");
    assert.match(problemsOf(() => seeded(JSON.stringify(everyContent), "tests")), /Content ID/);
    // the probe client must have no Content ID rows for the no-match test, which only the tests mode runs
    const probeContent = JSON.parse(RAW_STATE);
    probeContent.expectedSeries.push({ clientId: "probe-20261120T161252Z-85dcbd", identifierType: "content_id", identifierValue: "46", operation: "DESERIALIZE", count: 1 });
    assert.match(problemsOf(() => seeded(JSON.stringify(probeContent), "tests")), /probe/);
    assert.equal(seeded(JSON.stringify(probeContent), "screenshots").mode, "seeded", "the screenshot flows do not use the probe client");
    // the probe client must be a client Grafana offers: it needs at least one series of its own (the no-match test selects it)
    const noProbeRows = JSON.parse(RAW_STATE);
    noProbeRows.expectedSeries = noProbeRows.expectedSeries.filter((row) => row.clientId !== noProbeRows.clients.probe);
    assert.match(problemsOf(() => seeded(JSON.stringify(noProbeRows), "tests")), /probe client .* has no series/);
    assert.equal(seeded(JSON.stringify(noProbeRows), "screenshots").mode, "seeded", "the screenshot flows do not use the probe client");
    // no overlap between global and content numbers is required: only the conditional same-number case needs one
    const noOverlap = JSON.parse(RAW_STATE);
    noOverlap.schemas = [{ globalId: 46, contentId: 91 }, { globalId: 47, contentId: 92 }, { globalId: 48, contentId: 93 }];
    noOverlap.expectedSeries = noOverlap.expectedSeries.filter((row) => row.identifierType === "global_id");
    noOverlap.expectedSeries.push({ clientId: "orders-service", identifierType: "content_id", identifierValue: "91", operation: "DESERIALIZE", count: 2 });
    assert.equal(seeded(JSON.stringify(noOverlap), "tests").mode, "seeded");
    assert.throws(() => prepareBundle({ mode: "seeded", inputText: RAW_STATE }), /flows/, "a seeded input is prepared for one named set of flows");
    // the sealed side repeats the same judgment
    const prepared = seeded(RAW_STATE, "screenshots");
    assert.equal(prepared.flows, "screenshots");
    assert.equal(parseBundle(JSON.stringify({ ...JSON.parse(JSON.stringify(prepared)), flows: "tests" }), false).flows, "tests");
    // the process switch scenario needs at least one typed identifier that both processes reuse
    const noReuse = raw(RAW_SCENARIO, '"otherRows": [["c1", "global_id", "1"', '"otherRows": [["c1", "global_id", "2"');
    assert.doesNotThrow(() => parseProcessScenario(noReuse));
    assert.match(problemsOf(() => prepareBundle({ mode: "scenario", inputText: noReuse, urlOverride: "http://127.0.0.1:43000", defaultDatasourceUid: "ds" })), /reuse/);
    // the dependent client reload test narrows the second process to one client, so that process needs two different clients: a second
    // process with ONE client is refused even though it still shares a typed row, by the preparation and by the sealed bundle alike
    const oneClient = raw(RAW_SCENARIO, ', ["c2", "global_id", "1", "DESERIALIZE", 75]', "");
    assert.doesNotThrow(() => parseProcessScenario(oneClient), "the file itself is well formed");
    const prepareScenario = (text) => prepareBundle({ mode: "scenario", inputText: text, urlOverride: "http://127.0.0.1:43000", defaultDatasourceUid: "ds" });
    assert.equal(prepareScenario(RAW_SCENARIO).mode, "scenario", "two clients and a shared typed row prepare");
    assert.match(problemsOf(() => prepareScenario(oneClient)), /at least two different clients.*otherRows has 1/);
    assert.doesNotMatch(problemsOf(() => prepareScenario(oneClient)), /reuse/, "the typed row is still shared; only the client count is wrong");
    // two rows of the same client are still one client
    const oneClientTwoRows = raw(RAW_SCENARIO, '["c2", "global_id", "1", "DESERIALIZE", 75]', '["c1", "global_id", "1", "DESERIALIZE", 75]');
    assert.match(problemsOf(() => prepareScenario(oneClientTwoRows)), /otherRows has 1/);
    // the two processes must expect different rows, counts included and in any order: identical sets would let a panel that keeps reading
    // the first process pass the process switch checks. Refused by the preparation and by the sealed bundle alike.
    const identical = raw(RAW_SCENARIO, '"primaryRows": [["c1", "global_id", "1", "SERIALIZE", 1]]', '"primaryRows": [["c1", "global_id", "1", "SERIALIZE", 150], ["c2", "global_id", "1", "DESERIALIZE", 75]]');
    assert.doesNotThrow(() => parseProcessScenario(identical), "the file itself is well formed");
    assert.match(problemsOf(() => prepareScenario(identical)), /different rows.*the same set/);
    const reordered = raw(identical, '"primaryRows": [["c1", "global_id", "1", "SERIALIZE", 150], ["c2", "global_id", "1", "DESERIALIZE", 75]]', '"primaryRows": [["c2", "global_id", "1", "DESERIALIZE", 75], ["c1", "global_id", "1", "SERIALIZE", 150]]');
    assert.match(problemsOf(() => prepareScenario(reordered)), /different rows.*the same set/, "the same rows in another order are still the same set");
    // equal identifiers with another count are different rows (the table shows the count), and so are different identifiers
    const otherCount = raw(identical, '"primaryRows": [["c1", "global_id", "1", "SERIALIZE", 150]', '"primaryRows": [["c1", "global_id", "1", "SERIALIZE", 151]');
    assert.equal(prepareScenario(otherCount).mode, "scenario", "one count differs, so the switch is visible");
    assert.equal(prepareScenario(RAW_SCENARIO).mode, "scenario", "a valid, distinguishable scenario still prepares");
    const sealed = JSON.parse(JSON.stringify(prepareScenario(RAW_SCENARIO)));
    assert.equal(parseBundle(JSON.stringify(sealed), false).mode, "scenario");
    const sealedSame = JSON.parse(JSON.stringify(sealed));
    const sameProcesses = sealedSame.input.scenarios.find((entry) => entry.kind === "processes");
    sameProcesses.primaryRows = [...sameProcesses.otherRows].reverse();
    assert.match(problemsOf(() => parseBundle(JSON.stringify(sealedSame), false)), /different rows.*the same set/, "the sealed bundle repeats the judgment on reordered equal rows");
    const sealedValid = JSON.parse(JSON.stringify(sealed));
    assert.equal(parseBundle(JSON.stringify(sealedValid), false).mode, "scenario", "a sealed, distinguishable scenario reads back");
    // the bundle carries the validated input and is judged again from it
    const sealedProcesses = sealed.input.scenarios.find((entry) => entry.kind === "processes");
    sealedProcesses.otherRows = sealedProcesses.otherRows.filter((row) => row[0] === "c1");
    assert.equal(sealedProcesses.otherRows.length, 1, "the sealed input now shows one client in the second process");
    assert.match(problemsOf(() => parseBundle(JSON.stringify(sealed), false)), /at least two different clients/, "the sealed bundle repeats the judgment");
});

test("the Grafana datasource endpoint is built from the whole validated base path", async () => {
    const { datasourceQueryPath, matchesDatasourceQuery } = await import("../lib/grafana.mjs");
    assert.equal(datasourceQueryPath("http://127.0.0.1:13000"), "/api/ds/query");
    assert.equal(datasourceQueryPath("http://127.0.0.1:13000/grafana"), "/grafana/api/ds/query");
    assert.equal(datasourceQueryPath("https://example.org/tools/grafana/"), "/tools/grafana/api/ds/query");
    const at = (base, path) => matchesDatasourceQuery(base, new URL(`http://127.0.0.1:13000${path}`));
    assert.equal(at("http://127.0.0.1:13000", "/api/ds/query"), true);
    assert.equal(at("http://127.0.0.1:13000", "/grafana/api/ds/query"), false);
    assert.equal(at("http://127.0.0.1:13000/grafana", "/grafana/api/ds/query"), true);
    assert.equal(at("http://127.0.0.1:13000/grafana", "/api/ds/query"), false, "another path of the same host is not this Grafana");
    assert.equal(at("http://127.0.0.1:13000/grafana", "/grafana/api/ds/query/x"), false);
    assert.equal(at("http://127.0.0.1:13000/grafana", "/grafana2/api/ds/query"), false);
    assert.equal(matchesDatasourceQuery("http://127.0.0.1:13000", new URL("http://127.0.0.1:9999/api/ds/query")), false, "another origin");
});

test("the diagnostic commands need their directories, and one path rule serves the shell and the browser stage", () => {
    const run = (...args) => spawnSync(process.execPath, [cli, ...args], { encoding: "utf8" });
    for (const args of [["installed"], ["summary"], ["summary", "--run-dir", "/x"], ["verdict"]]) {
        const result = run(...args);
        assert.equal(result.status, 2, `${args.join(" ")} without its directories is a usage error, not a report on empty paths`);
        assert.match(result.stderr, /required/);
        assert.equal(result.stdout, "");
    }
    for (const [path, ok] of [
        ["/a/b", true],
        ["/a/b/", false],
        ["/a//b", false],
        ["/a/./b", false],
        ["/a/../b", false],
        ["a/b", false],
        ["/a/b\u0007", false],
        ["/a/b\nc", false],
        ["/a/b\u007f", false],
        ["", false],
    ]) {
        const result = run("path", "--absolute", path);
        assert.equal(result.status === 0, ok, `path ${JSON.stringify(path)}`);
    }
});

test("prepare writes exactly the serialized bundle on stdout and keeps warnings and errors on stderr", () => {
    const input = join(scratch, "state.json");
    writeFileSync(input, RAW_STATE);
    const warn = join(scratch, "warn.cjs");
    writeFileSync(warn, 'process.emitWarning("a harmless warning of the Node runtime");\n');
    const result = spawnSync(process.execPath, ["--require", warn, cli, "prepare", "--mode", "seeded", "--flows", "tests", "--input", input], { encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stderr, /harmless warning/);
    const bundle = JSON.parse(result.stdout);
    assert.equal(bundle.mode, "seeded");
    assert.equal(bundle.flows, "tests");
    const bad = spawnSync(process.execPath, [cli, "prepare", "--mode", "seeded", "--flows", "tests", "--input", join(scratch, "absent.json")], { encoding: "utf8" });
    assert.equal(bad.status, 2);
    assert.equal(bad.stdout, "");
    assert.match(bad.stderr, /cannot be read/);
    // the hash printed by seal is the hash of the file it wrote, and only that is on stdout
    const runDir = join(scratch, "run");
    mkdirSync(runDir);
    const sealed = spawnSync(process.execPath, ["--require", warn, cli, "seal", "--run-dir", runDir, "--report-dir", join(scratch, "report")], { encoding: "utf8", input: result.stdout });
    assert.equal(sealed.status, 0, sealed.stderr);
    assert.match(sealed.stdout, /^[0-9a-f]{64}\n$/);
    assert.equal(sealed.stdout.trim(), sha256Hex(spawnSync("cat", [join(runDir, "input.sealed.json")]).stdout));
});

test("artifact notes follow the artifacts that exist, and the report is offered only when it exists", () => {
    const dir = mkdtempSync(join(scratch, "notes-"));
    const empty = summaryLines({ runDir: join(dir, "none"), reportDir: join(dir, "none-report"), label: "x" }).join("\n");
    assert.doesNotMatch(empty, /sensitive|show-report/);
    const run = join(dir, "run");
    mkdirSync(join(run, "pw", "t"), { recursive: true });
    writeFileSync(join(run, "results.json"), JSON.stringify({ stats: { expected: 1, unexpected: 0, skipped: 0, flaky: 0 } }));
    const reportDir = join(dir, "report");
    mkdirSync(reportDir);
    const withoutIndex = summaryLines({ runDir: run, reportDir, label: "x" }).join("\n");
    assert.doesNotMatch(withoutIndex, /show-report/, "an empty report directory is not a report");
    assert.doesNotMatch(withoutIndex, /sensitive/, "no screenshot, trace or report exists, so there is nothing to warn about");
    writeFileSync(join(reportDir, "index.html"), "<html></html>");
    const withIndex = summaryLines({ runDir: run, reportDir, label: "x" }).join("\n");
    assert.match(withIndex, /open it: .*show-report/);
    assert.match(withIndex, /real operator reports, screenshots and traces can show/);
    assert.match(withIndex, /vetted synthetic/);
    assert.doesNotMatch(withIndex, /never publish them/);
});

test("number tokens are matched by unambiguous key and index segments, never by a dotted string", () => {
    const bad = (extra) => raw(RAW_STATE, '"endpoints"', `${extra}, "endpoints"`);
    // keys that merely look like the path of a consumed field are unknown fields, whatever they contain
    for (const key of ["timeRange.fromMs", "schemas[0].globalId", "expectedSeries[0].count", "schemaVersion.", ".schemaVersion", "a\\u002efromMs", "timeRange\\u002efromMs"]) {
        assert.equal(parseRunState(bad(`"${key}": 0.5`)).evaluationMs, 1795191182943, `an unknown top-level key ${key} with a fraction is not this tooling's business`);
    }
    // nested known fields are judged where they really are, in every spelling of the key
    assert.notEqual(problemsOf(() => parseRunState(raw(RAW_STATE, '"fromMs": 1795191142000', '"fromMs": 1795191142000.5'))), "");
    assert.notEqual(problemsOf(() => parseRunState(raw(RAW_STATE, '"timeRange": {"fromMs"', '"time\\u0052ange": {"fromMs"').replace('"fromMs": 1795191142000', '"fromMs": 1.5e12'))), "", "an escaped spelling of a consumed key is the same key");
    assert.notEqual(problemsOf(() => parseRunState(raw(RAW_STATE, '"globalId": 46, "contentId": 46}', '"globalId": 46, "contentId": 4.6e1}'))), "");
    // a key that contains the separators of the old dotted notation, holding an object with a consumed-looking name
    const lookalike = raw(RAW_STATE, '"endpoints"', '"timeRange.fromMs": {"x": 0.5}, "schemas[0]": {"globalId": 0.5, "contentId": 0.5}, "endpoints"');
    assert.equal(parseRunState(lookalike).fromMs, 1795191142000);
    // the process scenario: only its own consumed fields are strict, found by position and not by a dotted string
    const decoy = RAW_SCENARIO.replace('{"kind": "processes"', '{"kind": "rates", "scenarios[1].fromMs": 0.5, "fromMs": 0.5}, {"kind": "processes"');
    assert.equal(parseProcessScenario(decoy).primary, "p1");
    assert.notEqual(problemsOf(() => parseProcessScenario(raw(decoy, '"fromMs": 1795224000000', '"fromMs": 1795224000000.0'))), "");
});
