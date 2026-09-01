#! /usr/bin/env node
const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

// Resolved from this script's location rather than the working directory, so
// the check works regardless of where npm invokes it from.
const SDK_DIR = path.resolve(__dirname, "../../typescript-sdk");

// On a fresh clone the Kiota client has not been generated and the SDK has not
// been built, so the UI cannot resolve its "@sdk/..." imports.  If any of these
// are missing, generate and build before continuing.
const REQUIRED_FILES = [
    "lib/generated-client/models/index.ts",
    "lib/generated-client/apicurioRegistryClient.ts",
    "dist/main.js",
    "dist/generated-client/models/index.d.ts",
    "dist/generated-client/search/versions/index.d.ts"
];

const missing = REQUIRED_FILES.filter(file => !fs.existsSync(path.join(SDK_DIR, file)));

if (missing.length === 0) {
    console.info("typescript-sdk is already generated and built.  Nothing to do.");
    process.exit(0);
}

console.info("-------------------------------------------------------");
console.info("typescript-sdk is not built.  Missing:");
missing.forEach(file => console.info(`   ${file}`));
console.info("Generating sources and building.");
console.info("-------------------------------------------------------");

try {
    execSync("npm run generate-sources", { cwd: SDK_DIR, stdio: "inherit" });
    execSync("npm run build", { cwd: SDK_DIR, stdio: "inherit" });
} catch (error) {
    console.error("Failed to generate and build the typescript-sdk.");
    process.exit(error.status || 1);
}
