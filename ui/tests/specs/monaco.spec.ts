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
 * Installs a network observer that records the URL of every HTTP(S) request the page makes.
 * Use "findExternalRequests()" after the page has loaded to filter the recorded URLs down to
 * those that went to a host the Registry deployment does not own.
 */
function observeRequests(page: Page): string[] {
    const requests: string[] = [];

    page.on("request", request => {
        const url: URL = new URL(request.url());
        if (url.protocol === "http:" || url.protocol === "https:") {
            requests.push(request.url());
        }
    });

    return requests;
}

/**
 * Returns the subset of the given request URLs whose host is not part of the Registry deployment
 * under test. This is what actually matters for proving Monaco never falls back to an external CDN.
 *
 * The allowed hosts are: localhost (dev mode, where the UI and the mocked API run on different
 * local ports), the host of "REGISTRY_UI_URL" (the UI itself, which may be a remote deployment
 * such as an OpenShift route), and the host of the Registry API as configured in the UI's runtime
 * "config.js" (which may differ from the UI host when the UI and API are exposed separately).
 * Requests handled by "page.route()" still fire "request" events, so mocked API calls are covered
 * by the same host check.
 */
async function findExternalRequests(page: Page, requests: string[]): Promise<string[]> {
    const allowedHosts: Set<string> = new Set<string>(["localhost", "127.0.0.1", new URL(REGISTRY_UI_URL).hostname]);

    // "config.js" declares "ApicurioRegistryConfig" with a top-level "const", which creates a global
    // lexical binding rather than a "window" property, so it must be referenced by name.
    const apiUrl: string | undefined = await page.evaluate<string | undefined>(
        "typeof ApicurioRegistryConfig !== 'undefined' ? ApicurioRegistryConfig?.artifacts?.url : undefined"
    );
    if (apiUrl) {
        allowedHosts.add(new URL(apiUrl, page.url()).hostname);
    }

    return requests.filter(request => !allowedHosts.has(new URL(request).hostname));
}

test("Monaco - cold non-editor route does not request the Monaco runtime", async ({ page }) => {
    const requests: string[] = observeRequests(page);
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
    await page.route("**/apis/registry/v3/search/groups*", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ groups: [], count: 0 }) });
    });

    await page.goto(`${REGISTRY_UI_URL}/explore`);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    expect(localMonacoRequests).toEqual([]);
    expect(await findExternalRequests(page, requests)).toEqual([]);
});

test("Monaco - Content tab renders real content with no external requests", async ({ page }) => {
    const requests: string[] = observeRequests(page);
    await mockContentTabBackend(page);

    await page.goto(`${REGISTRY_UI_URL}/explore/${GROUP_ID}/${ARTIFACT_ID}/versions/${VERSION}/content`);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    // The Monaco editor renders the JSON content inside its own DOM, not via a plain <pre>/<textarea>,
    // so this specifically exercises the bundled runtime end to end (worker configuration, loader
    // initialization, and language registration), not just the raw text being present on the page.
    await expect(page.locator(".monaco-editor")).toBeVisible({ timeout: 15_000 });
    await expect(page.locator(".monaco-editor")).toContainText("\"answer\"");
    await expect(page.locator(".monaco-editor")).toContainText("42");

    expect(await findExternalRequests(page, requests)).toEqual([]);
});
