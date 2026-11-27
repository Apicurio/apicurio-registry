// Readiness of the browser as it will really be used: Chromium is launched, asked for its version and closed, and the
// executable it resolved must lie under the browser directory this invocation owns. A version flag or the presence of a
// directory proves nothing about a browser that cannot start.
import { realpathSync } from "node:fs";

import { chromium } from "@playwright/test";

import { isCleanAbsolutePath } from "./input.mjs";

const owned = process.env["PLAYWRIGHT_BROWSERS_PATH"];
if (!isCleanAbsolutePath(owned)) {
    process.stderr.write("PLAYWRIGHT_BROWSERS_PATH must be a clean absolute path; the runner sets it\n");
    process.exit(2);
}
const executable = chromium.executablePath();
let root = owned;
try {
    root = realpathSync(owned);
} catch {
    // the directory does not exist yet: the launch below reports that the browser is not installed
}
if (!executable.startsWith(`${owned}/`) && !executable.startsWith(`${root}/`)) {
    process.stderr.write(`the browser resolves to ${executable}, outside the owned browser directory ${owned}\n`);
    process.exit(2);
}
const browser = await chromium.launch();
const version = browser.version();
await browser.close();
process.stdout.write(`chromium ${version} at ${executable}\n`);
