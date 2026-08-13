import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

const MOCK_PROMPT_TEMPLATE = {
    templateId: "test-template",
    name: "Test Template",
    template: "Hello {{name}}, choose your {{color}}",
    variables: {
        color: {
            name: "color",
            type: "string",
            description: "A color",
            enum: ["red", "green", "blue"]
        },
        name: {
            name: "name",
            type: "string",
            description: "Your name"
        },
        emptyEnum: {
            name: "emptyEnum",
            type: "string",
            enum: []
        }
    }
};

const MOCK_VERSION_METADATA = {
    groupId: "default",
    artifactId: "test-prompt-template",
    version: "1",
    type: "PROMPT_TEMPLATE",
    artifactType: "PROMPT_TEMPLATE",
    state: "ENABLED",
    name: "Test Prompt Template",
    description: "A test prompt template",
    createdOn: "2026-08-05T00:00:00Z",
    createdBy: "test-user",
    labels: {}
};

test("Prompt Template - renders enum allowed values correctly", async ({ page }) => {
    // Mock system config, info, and user endpoints so the UI loads without a backend
    await page.route("**/apis/registry/v3/system/info", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ name: "Apicurio Registry", version: "3.3.2.Final" }) });
    });
    await page.route("**/apis/registry/v3/users/me", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ username: "test-user", displayName: "Test User", admin: true, developer: true, viewer: true }) });
    });
    await page.route("**/apis/registry/v3/config", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ features: { readOnly: false } }) });
    });

    // Mock the artifact metadata response
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(MOCK_VERSION_METADATA)
        });
    });

    // Mock the version metadata response so the UI knows it's a PROMPT_TEMPLATE
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/versions/1", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(MOCK_VERSION_METADATA)
        });
    });

    // Mock the content response to return our mock Prompt Template
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/versions/1/content*", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(MOCK_PROMPT_TEMPLATE)
        });
    });

    // Mock the artifact rules response to avoid errors
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/rules", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([])
        });
    });

    // Mock the branches response
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/branches", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([{ branchId: "latest", version: "1" }])
        });
    });
    
    // Navigate directly to the artifact documentation tab
    await page.goto(`${REGISTRY_UI_URL}/explore/default/test-prompt-template/versions/1/documentation`);

    // Verify the page title and that the template loaded
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await expect(page.getByText("Test Template")).toBeVisible();

    // Verify that the variables table is present
    const table = page.locator(".variables-table");
    await expect(table).toBeVisible();

    // Verify the color variable (has enum values)
    const colorRow = table.locator("tbody tr").filter({ hasText: "color" });
    await expect(colorRow).toBeVisible();
    await expect(colorRow.locator("td").nth(4)).toContainText("red");
    await expect(colorRow.locator("td").nth(4)).toContainText("green");
    await expect(colorRow.locator("td").nth(4)).toContainText("blue");
    // Verify it uses the patternfly label component
    await expect(colorRow.locator("td").nth(4).locator(".pf-v6-c-label").first()).toBeVisible();

    // Verify the name variable (no enum, should render '-')
    const nameRow = table.locator("tbody tr").filter({ hasText: "name" }).filter({ hasNotText: "emptyEnum" });
    await expect(nameRow).toBeVisible();
    await expect(nameRow.locator("td").nth(4)).toHaveText("-");

    // Verify the emptyEnum variable (empty array enum, should render '-')
    const emptyEnumRow = table.locator("tbody tr").filter({ hasText: "emptyEnum" });
    await expect(emptyEnumRow).toBeVisible();
    await expect(emptyEnumRow.locator("td").nth(4)).toHaveText("-");
});
