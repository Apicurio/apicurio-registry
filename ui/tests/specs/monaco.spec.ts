import { Page, test, expect } from "@playwright/test";
import { JSON_ARTIFACT_CONTENT } from "./data/monaco-content";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

const GROUP_ID: string = "default";
const ARTIFACT_ID: string = "monaco-test-artifact";
const VERSION: string = "1";

const MOCK_VERSION_METADATA = {
    groupId: GROUP_ID,
    artifactId: ARTIFACT_ID,
    version: VERSION,
    artifactType: "JSON",
    state: "ENABLED",
    name: "Monaco Test Artifact",
    description: "An artifact used to exercise the Monaco code editor.",
    createdOn: "2026-08-05T00:00:00Z",
    createdBy: "test-user",
    labels: {}
};

/**
 * Mocks every backend endpoint the Content tab needs, so these tests run without a running
 * Registry backend and can precisely control what content the editor renders.
 */
async function mockContentTabBackend(page: Page): Promise<void> {
    await page.route("**/apis/registry/v3/system/uiConfig", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) });
    });
    await page.route("**/apis/registry/v3/system/info", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ name: "Apicurio Registry", version: "3.3.2.Final" }) });
    });
    await page.route("**/apis/registry/v3/users/me", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ username: "test-user", displayName: "Test User", admin: true, developer: true, viewer: true }) });
    });
    await page.route("**/apis/registry/v3/config", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ features: { readOnly: false } }) });
    });
    await page.route(`**/apis/registry/v3/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}`, async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_VERSION_METADATA) });
    });
    await page.route(`**/apis/registry/v3/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/${VERSION}`, async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(MOCK_VERSION_METADATA) });
    });
    await page.route(`**/apis/registry/v3/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/${VERSION}/content*`, async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON_ARTIFACT_CONTENT });
    });
    await page.route(`**/apis/registry/v3/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/rules`, async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) });
    });
    await page.route(`**/apis/registry/v3/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/branches`, async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([{ branchId: "latest", version: VERSION }]) });
    });
}

/**
 * Installs a network observer that records every request whose *host* is not a local address.
 * This is what actually matters for proving Monaco never falls back to an external CDN: in this
 * dev-mode test setup the UI and (mocked) API run on different localhost ports, and every
 * request handled by "page.route()" still fires a "request" event even though it never reaches
 * a real network, so per-origin comparison against the UI's own origin would misclassify those
 * intercepted local API calls as "external". In a production deployment the UI and API share a
 * single origin (see "ui/.docker-scripts/nginx.conf"), so this only relaxes the local dev split;
 * it does not weaken the check that matters, which is that no *non-local* host is ever contacted.
 */
function observeExternalRequests(page: Page): string[] {
    const externalRequests: string[] = [];

    page.on("request", request => {
        const url: URL = new URL(request.url());
        const isLocal: boolean = url.hostname === "localhost" || url.hostname === "127.0.0.1";
        if ((url.protocol === "http:" || url.protocol === "https:") && !isLocal) {
            externalRequests.push(request.url());
        }
    });

    return externalRequests;
}

test("Monaco - cold non-editor route does not request the Monaco runtime", async ({ page }) => {
    const externalRequests: string[] = observeExternalRequests(page);
    const localMonacoRequests: string[] = [];
    page.on("request", request => {
        if (/monacoRuntime|PatternFlyEditorAdapter|\.worker[-.]/.test(request.url())) {
            localMonacoRequests.push(request.url());
        }
    });

    await page.route("**/apis/registry/v3/system/uiConfig", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) });
    });
    await page.route("**/apis/registry/v3/system/info", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ name: "Apicurio Registry", version: "3.3.2.Final" }) });
    });
    await page.route("**/apis/registry/v3/users/me", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ username: "test-user", displayName: "Test User", admin: true, developer: true, viewer: true }) });
    });
    await page.route("**/apis/registry/v3/config", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ features: { readOnly: false } }) });
    });
    await page.route("**/apis/registry/v3/search/artifacts*", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ artifacts: [], count: 0 }) });
    });

    await page.goto(`${REGISTRY_UI_URL}/explore`);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    expect(localMonacoRequests).toEqual([]);
    expect(externalRequests).toEqual([]);
});

test("Monaco - Content tab renders real content with no external requests", async ({ page }) => {
    const externalRequests: string[] = observeExternalRequests(page);
    await mockContentTabBackend(page);

    await page.goto(`${REGISTRY_UI_URL}/explore/${GROUP_ID}/${ARTIFACT_ID}/versions/${VERSION}/content`);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    // The Monaco editor renders the JSON content inside its own DOM, not via a plain <pre>/<textarea>,
    // so this specifically exercises the bundled runtime end to end (worker configuration, loader
    // initialization, and language registration), not just the raw text being present on the page.
    await expect(page.locator(".monaco-editor")).toBeVisible({ timeout: 15_000 });
    await expect(page.locator(".monaco-editor")).toContainText("\"answer\"");
    await expect(page.locator(".monaco-editor")).toContainText("42");

    expect(externalRequests).toEqual([]);
});
