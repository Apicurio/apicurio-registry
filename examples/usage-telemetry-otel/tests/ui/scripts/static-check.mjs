// Static rules of these tests, checked without running a browser: no fixed sleeps, no retries, no skipped or focused tests, no
// catch-and-continue around an assertion, no Basic Authorization header standing in for the form login, and no trace that records
// network headers (the one control test that shows the unsafe mode leaks is exempt). A fixed delay is allowed only in the one test that injects it as a fault.
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const rules = [
    [/waitForTimeout|setTimeout|setInterval|\bsleep\(/, "a fixed wait; use a web-first assertion or expect.poll", ["specs/variable-readiness.spec.ts", "specs/dependent-variable-readiness.spec.ts"]],
    [/\.only\(|\.skip\(|\.fixme\(|test\.fail\(/, "a focused, skipped or expected-to-fail test"],
    [/\bretries\s*:\s*[1-9]|test\.describe\.configure\([^)]*retries/, "retries must stay disabled"],
    [/\.catch\(|catch\s*(\(\w*\))?\s*\{\s*\}/, "an exception swallowed or ignored"],
    [/Authorization|extraHTTPHeaders|storageState/, "an authorization header or a stored session instead of the login form"],
    [/force\s*:\s*true/, "a forced action that skips actionability checks"],
    [/\bsnapshots\s*:\s*true/, "a trace with DOM snapshots records network headers, cookies included; use SAFE_TRACE_OPTIONS", "specs/trace-safety.spec.ts"],
];

const files = [];
const walk = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const path = join(directory, entry.name);
        if (entry.isDirectory() && entry.name !== "node_modules") walk(path);
        else if (entry.isFile() && path.endsWith(".ts")) files.push(path);
    }
};
for (const directory of ["specs", "support"]) walk(directory);
for (const entry of readdirSync("lib", { withFileTypes: true })) {
    if (entry.isFile() && entry.name.endsWith(".mjs")) files.push(join("lib", entry.name));
}
files.push("playwright.config.ts");

let problems = 0;
for (const file of files) {
    readFileSync(file, "utf8")
        .split("\n")
        .forEach((line, index) => {
            const code = line.replace(/\/\/.*$/, "");
            for (const [pattern, message, exemptFile] of rules) {
                if (![exemptFile].flat().includes(file) && pattern.test(code)) {
                    console.error(`${file}:${index + 1}: ${message}: ${line.trim()}`);
                    problems += 1;
                }
            }
        });
}
if (problems > 0) {
    console.error(`${problems} problem(s)`);
    process.exit(1);
}
console.log(`static check passed on ${files.length} files`);
