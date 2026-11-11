import { test, expect, Page, Request, Route } from "@playwright/test";
import { gunzipSync } from "zlib";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

const SERVER_PATH = "/apis/registry/v3/groups/servers/artifacts/weather";
const SECRET = "Bearer e2e-s3cr3t";

/** The request body as JSON. The registry client gzips request bodies. */
const bodyOf = (request: Request): unknown => {
    const raw = request.postDataBuffer() as Buffer;
    const gzipped = (request.headers()["content-encoding"] || "").includes("gzip");
    return JSON.parse((gzipped ? gunzipSync(raw) : raw).toString("utf8"));
};

const json = (route: Route, status: number, body: unknown) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

const RESULT = {
    groupId: "servers",
    artifactId: "weather",
    status: "NEW_VERSION",
    version: "2",
    tools: [
        { name: "getWeather", groupId: "servers", artifactId: "weather.getWeather", version: "1", status: "CREATED" },
        {
            name: "getForecast", groupId: "servers", artifactId: "weather.getForecast", status: "REJECTED",
            causes: [{ description: "The declared 'type' of the schema was changed.", context: "/inputSchema/properties/days/type" }]
        }
    ]
};

/**
 * Mocks the registry calls of the artifact page. Records each import request body.
 */
const mockRegistry = async (page: Page, options: { mcpImport: boolean, artifactType?: string }): Promise<Request[]> => {
    const imports: Request[] = [];
    await page.route("**/apis/registry/v3/**", async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path.endsWith("/system/uiConfig")) {
            return json(route, 200, {
                ui: { contextPath: "/", navPrefixPath: "/" },
                auth: { type: "none", rbacEnabled: false, obacEnabled: false },
                features: { readOnly: false, breadcrumbs: true, mcpImport: options.mcpImport }
            });
        }
        if (path === SERVER_PATH) {
            return json(route, 200, {
                groupId: "servers", artifactId: "weather", artifactType: options.artifactType || "MCP_SERVER",
                owner: "", createdOn: "2026-10-01T00:00:00Z"
            });
        }
        if (path === `${SERVER_PATH}/mcp-import` && request.method() === "POST") {
            imports.push(request);
            return json(route, 200, RESULT);
        }
        if (path === `${SERVER_PATH}/rules`) {
            return json(route, 200, []);
        }
        if (path.endsWith("/search/versions")) {
            return json(route, 200, { count: 0, versions: [] });
        }
        if (path.endsWith("/admin/config/artifactTypes")) {
            return json(route, 200, [{ name: "MCP_SERVER" }, { name: "MCP_TOOL" }]);
        }
        return json(route, 404, { status: 404, title: "Not mocked", name: "NotFoundException" });
    });
    return imports;
};

test("MCP import - the button is offered only for MCP_SERVER artifacts when the server enables it", async ({ page }) => {
    await mockRegistry(page, { mcpImport: false });
    await page.goto(`${REGISTRY_UI_URL}/explore/servers/weather`);
    await expect(page.getByTestId("artifact-overview-tab")).toBeVisible();
    await expect(page.getByTestId("header-btn-import-mcp-tools")).toHaveCount(0);

    await page.unrouteAll();
    await mockRegistry(page, { mcpImport: true, artifactType: "MCP_TOOL" });
    await page.goto(`${REGISTRY_UI_URL}/explore/servers/weather`);
    await expect(page.getByTestId("artifact-overview-tab")).toBeVisible();
    await expect(page.getByTestId("header-btn-import-mcp-tools")).toHaveCount(0);
});

test("MCP import - imports with headers and shows each tool's result", async ({ page }) => {
    const imports = await mockRegistry(page, { mcpImport: true });
    await page.goto(`${REGISTRY_UI_URL}/explore/servers/weather`);

    await page.getByTestId("header-btn-import-mcp-tools").click();
    await page.getByTestId("form-mcp-url").fill("https://weather.example.com/mcp");
    await page.getByTestId("form-mcp-header-add").click();
    await page.getByTestId("form-mcp-header-name-0").fill("Authorization");
    await page.getByTestId("form-mcp-header-value-0").fill(SECRET);
    await page.getByTestId("modal-btn-import").click();

    await expect(page.getByTestId("mcp-import-tool-getWeather")).toContainText("CREATED");
    await expect(page.getByTestId("mcp-import-tool-getForecast")).toContainText("REJECTED");
    await expect(page.getByTestId("mcp-import-rejected-getForecast"))
        .toContainText("The declared 'type' of the schema was changed.");
    await expect(page.getByTestId("mcp-import-summary")).toContainText("now at version 2.");

    expect(imports).toHaveLength(1);
    expect(bodyOf(imports[0])).toEqual({
        url: "https://weather.example.com/mcp",
        dryRun: false,
        headers: { Authorization: SECRET }
    });
    // The MCP server headers travel in the body only. Sent as HTTP headers, they would reach the
    // registry, and a real browser would block the request in the CORS preflight.
    const httpHeaders = await imports[0].allHeaders();
    expect(Object.keys(httpHeaders)).not.toContain("additionaldata");
    expect(httpHeaders["authorization"]).toBeUndefined();
    await expect(page.getByTestId("form-mcp-header-value-0")).toHaveAttribute("type", "password");
    expect(await page.locator("body").innerText()).not.toContain(SECRET);
});
