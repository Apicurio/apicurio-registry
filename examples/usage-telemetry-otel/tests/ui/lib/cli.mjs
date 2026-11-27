// Command line of the input and tooling boundary, for the shell runner. Structured results go to stdout only; diagnostics go to
// stderr, so a warning printed by the Node runtime can never end up inside a result. Every subcommand reads what it needs once and
// fails with exit 2 and one line per problem on stderr when its input or its arguments are not usable.
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

import { InputError, isCleanAbsolutePath, parseBundle, prepareBundle, sealBundle, serializeBundle, sha256Hex } from "./input.mjs";
import { browserWriteProblems, installedProblems, summaryLines, verdictProblems } from "./tooling.mjs";

/** @param {string[]} args @param {string} name @returns {string | undefined} */
const option = (args, name) => {
    const index = args.indexOf(name);
    return index >= 0 ? args[index + 1] : undefined;
};
/** An argument the subcommand cannot do without: absent is a usage error, never an empty path.
 * @param {string[]} args @param {string} name @returns {string} */
const required = (args, name) => {
    const value = option(args, name);
    if (value === undefined || value === "") {
        throw new InputError([`${name} is required`]);
    }
    return value;
};

const [command, ...args] = process.argv.slice(2);
try {
    switch (command) {
        case "prepare": {
            // The input file is read here, once; its validated content travels on as the prepared bundle
            const mode = option(args, "--mode");
            if (mode !== "seeded" && mode !== "scenario" && mode !== "selftest") {
                throw new InputError(["--mode must be seeded, scenario or selftest"]);
            }
            const file = mode === "selftest" ? undefined : required(args, "--input");
            try {
                let inputText;
                if (file !== undefined) {
                    try {
                        inputText = readFileSync(file, "utf8");
                    } catch (error) {
                        throw new InputError([`${file} cannot be read: ${/** @type {Error} */ (error).message}`]);
                    }
                }
                const flows = option(args, "--flows");
                const bundle = prepareBundle({
                    mode,
                    ...(flows === "tests" || flows === "screenshots" ? { flows } : {}),
                    ...(inputText === undefined ? {} : { inputText }),
                    ...(option(args, "--grafana-url") === undefined ? {} : { urlOverride: /** @type {string} */ (option(args, "--grafana-url")) }),
                    ...(option(args, "--datasource-uid") === undefined ? {} : { datasourceOverride: /** @type {string} */ (option(args, "--datasource-uid")) }),
                    ...(option(args, "--default-datasource-uid") === undefined ? {} : { defaultDatasourceUid: /** @type {string} */ (option(args, "--default-datasource-uid")) }),
                });
                process.stdout.write(`${serializeBundle(bundle)}\n`);
            } catch (error) {
                if (error instanceof InputError) {
                    error.problems.unshift(`${file ?? "the self-test input"} is not usable:`);
                }
                throw error;
            }
            break;
        }
        case "seal": {
            const runDir = required(args, "--run-dir");
            const reportDir = required(args, "--report-dir");
            const text = sealBundle(readFileSync(0, "utf8"), runDir, reportDir);
            writeFileSync(join(runDir, "input.sealed.json"), text, { flag: "wx", mode: 0o444 });
            process.stdout.write(`${sha256Hex(text)}\n`);
            break;
        }
        case "describe": {
            // What is being tested and where, for the operator: never a credential
            const bundle = parseBundle(readFileSync(0, "utf8"), false);
            const identity = bundle.state !== undefined ? `run ${bundle.state.runId}, Registry process ${bundle.state.instanceId}` : bundle.scenario !== undefined ? `processes scenario ${bundle.scenario.primary} and ${bundle.scenario.other}` : "the browser self-tests (no Grafana, no stack)";
            process.stdout.write(`${option(args, "--runner") ?? "the runner"} on ${identity}\n`);
            if (bundle.grafanaUrl !== null) {
                process.stdout.write(`Grafana: ${bundle.grafanaUrl} (${bundle.grafanaUrlSource}), datasource ${bundle.datasourceUid} (${bundle.datasourceSource})\n`);
            }
            break;
        }
        case "path": {
            // The one rule for a directory the tooling writes to; the shell asks instead of keeping a rule of its own
            const value = option(args, "--absolute");
            if (!isCleanAbsolutePath(value)) {
                throw new InputError(["must be an absolute path without a '.' or '..' part, a repeated or trailing slash, or control characters"]);
            }
            break;
        }
        case "installed": {
            // The problems are the result: one per line on stdout, status 1
            const problems = installedProblems(required(args, "--dir"));
            for (const problem of problems) {
                process.stdout.write(`${problem}\n`);
            }
            process.exitCode = problems.length === 0 ? 0 : 1;
            break;
        }
        case "browser-destinations": {
            // The installer's own dry-run report arrives on stdin; what would make Playwright write elsewhere is the result
            const mode = required(args, "--for");
            if (mode !== "launch" && mode !== "install") {
                throw new InputError(["--for must be launch or install"]);
            }
            const problems = browserWriteProblems(required(args, "--root"), readFileSync(0, "utf8"), mode);
            for (const problem of problems) {
                process.stdout.write(`${problem}\n`);
            }
            process.exitCode = problems.length === 0 ? 0 : 1;
            break;
        }
        case "summary": {
            // Diagnostics only: whatever is missing is reported as missing and the status stays the caller's
            const uiDir = option(args, "--ui-dir");
            process.stdout.write(
                `${summaryLines({ runDir: required(args, "--run-dir"), reportDir: required(args, "--report-dir"), label: option(args, "--label") ?? "this invocation", ...(uiDir === undefined ? {} : { uiDir }) }).join("\n")}\n`,
            );
            break;
        }
        case "verdict": {
            const screenshots = (option(args, "--screenshots") ?? "").split(",").filter((name) => name !== "");
            const problems = verdictProblems({ runDir: required(args, "--run-dir"), screenshots });
            for (const problem of problems) {
                process.stderr.write(`error: ${problem}\n`);
            }
            process.exitCode = problems.length === 0 ? 0 : 1;
            break;
        }
        default:
            throw new InputError([`unknown subcommand '${command}'`]);
    }
} catch (error) {
    if (error instanceof InputError) {
        for (const problem of error.problems) {
            process.stderr.write(`${problem}\n`);
        }
        process.exitCode = 2;
    } else {
        process.stderr.write(`${/** @type {Error} */ (error).stack ?? error}\n`);
        process.exitCode = 70;
    }
}
