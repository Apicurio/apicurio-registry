import { test, expect } from "@playwright/test";
import { PROMPT_TEMPLATE_DATA } from "./data/promptTemplate-simple";

const PROMPT_TEMPLATE_DATA_STR: string = JSON.stringify(PROMPT_TEMPLATE_DATA, null, 4);

const REGISTRY_UI_URL: string = (globalThis as any).process?.env?.["REGISTRY_UI_URL"] || "http://localhost:8888";

const TEST_ARTIFACT = "TestPromptTemplate";
const TEST_VERSION = "1.0.0";

test("Prompt Template - viewer and test panel", async ({ page }) => {
    test.setTimeout(60000);

    const TEST_GROUP = `PromptTemplateTest${Date.now()}`;
    const cleanup = async (): Promise<void> => {
        try {
            await page.request.delete(`${REGISTRY_UI_URL}/apis/registry/v3/groups/${TEST_GROUP}/artifacts/${TEST_ARTIFACT}`);
        } finally {
            await page.request.delete(`${REGISTRY_UI_URL}/apis/registry/v3/groups/${TEST_GROUP}`);
        }
    };

    try {
        await page.goto(`${REGISTRY_UI_URL}/explore`);
        await expect(page).toHaveTitle(/Apicurio Registry/);

        await page.getByTestId("btn-toolbar-create-group").click();
        await page.getByTestId("create-group-groupId").fill(TEST_GROUP);
        await page.getByTestId("create-group-modal-btn-create").click();
        await expect(page).toHaveURL(new RegExp(`.+/explore/${TEST_GROUP}`));

        await page.getByTestId("btn-create-artifact").click();
        await page.getByTestId("create-artifact-modal-id").fill(TEST_ARTIFACT);
        await page.getByTestId("create-artifact-modal-type-select").click();
        await page.getByTestId("create-artifact-modal-PROMPT_TEMPLATE").click();
        await page.locator("#next-wizard-page").click();

        await page.getByTestId("create-artifact-modal-artifact-metadata-name").fill("Test Prompt Template");
        await page.locator("#next-wizard-page").click();

        await page.getByTestId("create-artifact-modal-version").fill(TEST_VERSION);
        await page.locator("#artifact-content").fill(PROMPT_TEMPLATE_DATA_STR);
        await page.locator("#next-wizard-page").click();
        await page.locator("#next-wizard-page").click();
        await page.locator("#next-wizard-page").click();

        await expect(page).toHaveURL(new RegExp(`.+/explore/${TEST_GROUP}/${TEST_ARTIFACT}`));

        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/${TEST_ARTIFACT}/versions/${TEST_VERSION}`);
        await expect(page).toHaveTitle(/Apicurio Registry/);

        await page.getByTestId("version-documentation-tab").click();

        await expect(page.getByRole("heading", { name: PROMPT_TEMPLATE_DATA.name })).toBeVisible();
        await expect(page.getByText(/Answer the following question/)).toBeVisible();

        const variablesTable = page.getByRole("table", { name: "Template variables" });
        await expect(variablesTable).toBeVisible();
        await expect(variablesTable.getByText("question", { exact: true })).toBeVisible();
        await expect(variablesTable.getByText("include_examples", { exact: true })).toBeVisible();

        await expect(page.getByRole("heading", { name: "Test Prompt" })).toBeVisible();
        const questionInput = page.getByLabel("question", { exact: false });
        const includeExamplesCheckbox = page.locator("#var-include_examples");
        await expect(questionInput).toBeVisible();
        await expect(includeExamplesCheckbox).toBeVisible();
        await expect(includeExamplesCheckbox).toBeChecked();

        const renderButton = page.getByRole("button", { name: "Render" });

        const renderedOutputBox = page.locator(".rendered-output");

        // Required variables currently render as empty strings when blank.
        // Validation is tracked separately from this coverage test.
        await renderButton.click();
        await expect(page.getByRole("heading", { name: "Rendered Output" })).toBeVisible({ timeout: 15000 });
        await expect(renderedOutputBox).toHaveText("Answer the following question:");

        // Now fill in the variable and confirm the substitution actually happens.
        await questionInput.fill("What is Apicurio Registry?");
        await renderButton.click();
        await expect(renderedOutputBox).toHaveText("Answer the following question: What is Apicurio Registry?");
    } finally {
        await cleanup();
    }
});
