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
    await page.route("**/apis/registry/v3/system/info", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ name: "Apicurio Registry", version: "3.3.2.Final" }) });
    });
    await page.route("**/apis/registry/v3/users/me", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ username: "test-user", displayName: "Test User", admin: true, developer: true, viewer: true }) });
    });
    await page.route("**/apis/registry/v3/config", async route => {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ features: { readOnly: false } }) });
    });
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(MOCK_VERSION_METADATA)
        });
    });
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/versions/1", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(MOCK_VERSION_METADATA)
        });
    });
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/versions/1/content*", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(MOCK_PROMPT_TEMPLATE)
        });
    });
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/rules", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([])
        });
    });
    await page.route("**/apis/registry/v3/groups/default/artifacts/test-prompt-template/branches", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([{ branchId: "latest", version: "1" }])
        });
    });

    await page.goto(`${REGISTRY_UI_URL}/explore/default/test-prompt-template/versions/1/documentation`);

    await expect(page).toHaveTitle(/Apicurio Registry/);
    await expect(page.getByText("Test Template")).toBeVisible();

    const table = page.locator(".variables-table");
    await expect(table).toBeVisible();

    const colorRow = table.locator("tbody tr").filter({ hasText: "color" });
    await expect(colorRow).toBeVisible();
    await expect(colorRow.locator("td").nth(4)).toContainText("red");
    await expect(colorRow.locator("td").nth(4)).toContainText("green");
    await expect(colorRow.locator("td").nth(4)).toContainText("blue");
    await expect(colorRow.locator("td").nth(4).locator(".pf-v6-c-label").first()).toBeVisible();

    const nameRow = table.locator("tbody tr").filter({ hasText: "name" }).filter({ hasNotText: "emptyEnum" });
    await expect(nameRow).toBeVisible();
    await expect(nameRow.locator("td").nth(4)).toHaveText("-");

    const emptyEnumRow = table.locator("tbody tr").filter({ hasText: "emptyEnum" });
    await expect(emptyEnumRow).toBeVisible();
    await expect(emptyEnumRow.locator("td").nth(4)).toHaveText("-");
});

test.describe("Prompt Template Playground", () => {
  test("should load prompt template and render variables", async ({ page }) => {
    const templateContent = {
      template:
        "You are a helpful assistant. Answer the following question: {{question}}",
      variables: {
        question: {
          type: "string",
          description: "The prompt or question to ask",
          required: true,
        },
      },
    };
    await page.goto("/explore");
    await page.getByTestId("dashboard-tab").click();
    await page.getByTestId("quick-action-create").click();
    await page.getByTestId("create-artifact-modal-type-select").click();
    await page.locator("button").filter({ hasText: "Prompt Template" }).click();

    await page.getByRole("button", { name: "Next" }).click();
    await page.getByRole("button", { name: "Next" }).click();

    await page.getByRole("textbox", { name: "File upload" }).click();
    await page
      .getByRole("textbox", { name: "File upload" })
      .fill(JSON.stringify(templateContent));

    await page.getByRole("button", { name: "Next" }).click();
    await page.getByRole("button", { name: "Next" }).click();
    await page.getByRole("button", { name: "Create" }).click();

    await page.locator('[data-testid^="version-actions-"]').click();
    await page.locator('[data-testid^="view-version-"]').click();
    await page.getByTestId("version-documentation-tab").click();

    await page.getByRole("textbox", { name: "question" }).click();
    await page.getByRole("textbox", { name: "question" }).fill("Hello");

    await page.getByRole("button", { name: "Render" }).click();
    await expect(
      page.getByText(
        "You are a helpful assistant. Answer the following question: Hello",
      ),
    ).toBeVisible();
  });
});