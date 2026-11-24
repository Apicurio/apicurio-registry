// Checks of the local tooling that need no npm package: which locked packages are really installed, which environment
// variables would let a caller redirect Playwright's reporters, and what a finished run left behind. Dependency-free, like
// input.mjs.
import { accessSync, constants, existsSync, lstatSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

import { isCleanAbsolutePath } from "./input.mjs";

/**
 * Every locked package must be installed at the locked version, not only the wrapper that carries the name: the CLI, the
 * browser installer and the launcher are different packages. Optional entries (platform specific ones such as fsevents) may be
 * absent. Returns the problems found, empty when the install is whole.
 * @param {string} dir the package directory (the one with package.json, package-lock.json and node_modules)
 * @returns {string[]}
 */
export function installedProblems(dir) {
    const problems = [];
    const lockPath = join(dir, "package-lock.json");
    if (!existsSync(lockPath)) {
        return [`${lockPath} does not exist`];
    }
    const lock = JSON.parse(readFileSync(lockPath, "utf8"));
    for (const [key, entry] of Object.entries(lock.packages ?? {})) {
        if (!key.startsWith("node_modules/") || typeof entry !== "object" || entry === null) {
            continue;
        }
        const wanted = /** @type {{ version?: string, optional?: boolean }} */ (entry);
        const manifest = join(dir, key, "package.json");
        if (!existsSync(manifest)) {
            if (wanted.optional !== true) {
                problems.push(`${key} is not installed (locked ${wanted.version})`);
            }
            continue;
        }
        let installed;
        try {
            installed = JSON.parse(readFileSync(manifest, "utf8")).version;
        } catch {
            problems.push(`${key}/package.json cannot be read`);
            continue;
        }
        if (installed !== wanted.version) {
            problems.push(`${key} is ${installed}, locked ${wanted.version}`);
        }
    }
    const bin = join(dir, "node_modules", ".bin", "playwright");
    try {
        accessSync(bin, constants.X_OK);
    } catch {
        problems.push("node_modules/.bin/playwright is missing or not executable");
    }
    return problems;
}

/**
 * Environment variables that make a Playwright reporter write somewhere other than where the configuration says, add a
 * reporter, or open a report server. The reporters read them before the explicit configuration (pinned 1.65.1 source:
 * reportFolderFromEnv, getHtmlReportOptionProcessEnv, resolveOutputFile, PW_TEST_REPORTER, PLAYWRIGHT_LAST_RUN_OUTPUT_FILE).
 * @param {Record<string, string | undefined>} env @returns {string[]} the names that are set, never their values
 */
export function reporterOverrides(env) {
    const exact = new Set([
        "PLAYWRIGHT_HTML_OUTPUT_DIR",
        "PLAYWRIGHT_HTML_REPORT",
        "PLAYWRIGHT_HTML_OPEN",
        "PW_TEST_HTML_REPORT_OPEN",
        "PLAYWRIGHT_HTML_HOST",
        "PLAYWRIGHT_HTML_PORT",
        "PLAYWRIGHT_HTML_ATTACHMENTS_BASE_URL",
        "PLAYWRIGHT_HTML_TITLE",
        "PW_TEST_REPORTER",
        "PLAYWRIGHT_LAST_RUN_OUTPUT_FILE",
    ]);
    return Object.keys(env)
        .filter((name) => env[name] !== undefined && (exact.has(name) || /^PLAYWRIGHT_[A-Z]+_OUTPUT_(FILE|DIR|NAME)$/.test(name)))
        .sort();
}

/** @param {string} directory @param {(name: string) => boolean} wanted @returns {string[]} */
function findFiles(directory, wanted) {
    if (!existsSync(directory)) {
        return [];
    }
    /** @type {string[]} */
    const found = [];
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            found.push(...findFiles(path, wanted));
        } else if (wanted(entry.name)) {
            found.push(path);
        }
    }
    return found.sort();
}

/**
 * @param {string} path
 * @returns {{ stats: { expected: number, unexpected: number, skipped: number, flaky: number }, coverage: string[] } | undefined}
 */
export function readResults(path) {
    if (!existsSync(path)) {
        return undefined;
    }
    let results;
    try {
        results = JSON.parse(readFileSync(path, "utf8"));
    } catch {
        return undefined;
    }
    const stats = results?.stats;
    if (typeof stats !== "object" || stats === null) {
        return undefined;
    }
    /** @type {string[]} */
    const coverage = [];
    /** @param {any} suite */
    const walk = (suite) => {
        for (const spec of suite.specs ?? []) {
            for (const test of spec.tests ?? []) {
                for (const note of test.annotations ?? []) {
                    if (note.type === "coverage") {
                        coverage.push(`${spec.title}: ${note.description}`);
                    }
                }
            }
        }
        for (const child of suite.suites ?? []) {
            walk(child);
        }
    };
    for (const suite of results.suites ?? []) {
        walk(suite);
    }
    return { stats: { expected: stats.expected ?? 0, unexpected: stats.unexpected ?? 0, skipped: stats.skipped ?? 0, flaky: stats.flaky ?? 0 }, coverage };
}

/**
 * Honest lines about what a finished invocation left: only what exists is listed, and a missing directory is reported as
 * missing, never an error. The notes about sensitive artifacts follow the artifacts that were found, and the command that opens
 * the report is offered only when its index.html exists.
 * @param {{ runDir: string, reportDir: string, label: string, uiDir?: string }} where @returns {string[]}
 */
export function summaryLines(where) {
    const { runDir, reportDir, label, uiDir } = where;
    const lines = [`Result of ${label}:`];
    const results = readResults(join(runDir, "results.json"));
    if (results === undefined) {
        lines.push("  no results.json: the browser run did not get as far as running tests");
    } else {
        const { expected, unexpected, skipped, flaky } = results.stats;
        lines.push(`  tests: ${expected} passed, ${unexpected} failed, ${skipped} skipped, ${flaky} flaky`);
        for (const note of results.coverage) {
            lines.push(`  coverage: ${note}`);
        }
    }
    const index = join(reportDir, "index.html");
    const reportExists = existsSync(index);
    const shots = findFiles(join(runDir, "screenshots"), (name) => name.endsWith(".png"));
    const failureShots = findFiles(join(runDir, "pw"), (name) => /^test-failed-.*\.png$/.test(name));
    const traces = findFiles(join(runDir, "pw"), (name) => name === "trace.zip");
    lines.push(reportExists ? `  report:      ${index}` : "  report:      none was written");
    lines.push(...shots.map((file) => `  screenshot:  ${file}`));
    lines.push(...failureShots.map((file) => `  failure screenshot: ${file}`));
    lines.push(...traces.map((file) => `  trace:       ${file}`));
    if (reportExists) {
        lines.push(`  open it:     ${uiDir === undefined ? "" : `cd ${uiDir} && `}npx playwright show-report ${reportDir}`);
    }
    if (reportExists || shots.length > 0 || failureShots.length > 0 || traces.length > 0) {
        lines.push(
            "  note: real operator reports, screenshots and traces can show dashboard text, URLs, client IDs and identifiers of the stack under test; keep them local and do not upload or attach them unreviewed. Only vetted synthetic demo artifacts, such as the walkthrough images of the guide, are meant to be shared.",
            "  note: a trace starts only after the sign-in was accepted, so a failure before that leaves a screenshot and no trace (GUIDE.md, Traces and what they may contain).",
        );
    }
    return lines;
}

/**
 * The install locations the pinned installer reports for `playwright install --dry-run chromium`: the directories it would create
 * below the browser directory.
 * @param {string} dryRunText @returns {string[]}
 */
export function installLocations(dryRunText) {
    return [...dryRunText.matchAll(/^[ \t]*Install location:[ \t]+(\S.*?)[ \t]*$/gm)].map((match) => /** @type {string} */ (match[1]));
}

/** The files the pinned Playwright writes inside an install location: the browser launcher writes the first when it validates the
 * host, the installer writes the second when it finishes. */
const LOCATION_MARKERS = ["DEPENDENCIES_VALIDATED", "INSTALLATION_COMPLETE"];

/**
 * What exists before Playwright runs and would make it write somewhere else. "launch": the browser launcher (the readiness probe and
 * the tests) writes the marker file DEPENDENCIES_VALIDATED in the directory of each browser it validates, through that directory and
 * through whatever a link in the marker's place points to, so each install location the installer reports must be an ordinary
 * directory inside the browser directory and each marker that already exists in it must be an ordinary file. "install": the same, and
 * in addition the pinned installer writes its package reference below .links and takes a lock in __dirlock, so those must not be links
 * or non-directories either, and every entry of .links must be an ordinary file (the installer would block opening a FIFO). The contents of an existing install location are not inspected beyond its markers: a browser
 * application bundle legitimately holds links of its own. This is a check of the state before Playwright starts; it does not
 * prevent a link that another process creates meanwhile.
 * @param {string} root the absolute browser directory @param {string} dryRunText the installer's own dry-run report
 * @param {"launch" | "install"} mode @returns {string[]}
 */
export function browserWriteProblems(root, dryRunText, mode) {
    /** @type {string[]} */
    const problems = [];
    /** @param {string} path @param {string} what @param {"directory" | "file"} kind @returns {boolean} whether it exists as such */
    const check = (path, what, kind) => {
        let stat;
        try {
            stat = lstatSync(path);
        } catch {
            return false;
        }
        if (stat.isSymbolicLink()) {
            problems.push(`${what} ${path} is a symbolic link; Playwright would write through it`);
            return false;
        }
        if (kind === "directory" ? !stat.isDirectory() : !stat.isFile()) {
            problems.push(`${what} ${path} exists but is not a ${kind === "directory" ? "directory" : "regular file"}`);
            return false;
        }
        return true;
    };
    const locations = installLocations(dryRunText);
    if (locations.length === 0) {
        problems.push("the installer reported no install location, so where Playwright would write could not be checked");
    }
    for (const location of locations) {
        if (!isCleanAbsolutePath(location) || !location.startsWith(`${root}/`) || location.slice(root.length + 1).includes("/")) {
            problems.push(`the installer would install into ${location}, outside the browser directory ${root}`);
            continue;
        }
        check(location, "the install location", "directory");
        for (const marker of LOCATION_MARKERS) {
            check(join(location, marker), "the marker file", "file");
        }
    }
    if (mode === "install") {
        check(join(root, "__dirlock"), "the installer's lock directory", "directory");
        if (check(join(root, ".links"), "the installer's .links directory", "directory")) {
            for (const entry of readdirSync(join(root, ".links"))) {
                check(join(root, ".links", entry), "the .links entry", "file");
            }
        }
    }
    return problems;
}

/**
 * Whether a run that exited 0 really passed: tests ran, none failed, none skipped, none flaky, and every wanted screenshot is a
 * real PNG. Returns the reasons it did not, empty when it did.
 * @param {{ runDir: string, screenshots: string[] }} where @returns {string[]}
 */
export function verdictProblems(where) {
    const problems = [];
    const results = readResults(join(where.runDir, "results.json"));
    if (results === undefined) {
        return ["Playwright exited 0 but wrote no readable results.json"];
    }
    const { expected, unexpected, skipped, flaky } = results.stats;
    if (expected === 0 || unexpected !== 0 || skipped !== 0 || flaky !== 0) {
        problems.push("Playwright exited 0 but not every test ran and passed");
    }
    for (const name of where.screenshots) {
        const path = join(where.runDir, "screenshots", `${name}.png`);
        const signature = existsSync(path) && statSync(path).size > 8 ? readFileSync(path).subarray(0, 8).toString("hex") : "";
        if (signature !== "89504e470d0a1a0a") {
            problems.push(`${path} was not written as a PNG`);
        }
    }
    return problems;
}
