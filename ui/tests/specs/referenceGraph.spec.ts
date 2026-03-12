import { test, expect } from "@playwright/test";
import { OPENAPI_DATA } from "./data/openapi-simple";

const OPENAPI_DATA_STR: string = JSON.stringify(OPENAPI_DATA, null, 4);

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

// Test artifact details
const TEST_ARTIFACT = "TestArtifact";
const TEST_VERSION = "1.0.0";

test("Reference Graph - view mode toggle and graph view", async ({ page }) => {
    test.setTimeout(60000); // This test needs more time as it creates resources

    // Use a unique group name to avoid conflicts with other runs
    const TEST_GROUP = `RefGraphTest${Date.now()}`;
    // Step 1: Create a group
    await page.goto(`${REGISTRY_UI_URL}/explore`);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    await page.getByTestId("btn-toolbar-create-group").click();
    await page.getByTestId("create-group-groupId").fill(TEST_GROUP);
    await page.getByTestId("create-group-modal-btn-create").click();
    await expect(page).toHaveURL(new RegExp(`.+/explore/${TEST_GROUP}`));

    // Step 2: Create an artifact
    await page.getByTestId("btn-create-artifact").click();
    await page.getByTestId("create-artifact-modal-id").fill(TEST_ARTIFACT);
    await page.getByTestId("create-artifact-modal-type-select").click();
    await page.getByTestId("create-artifact-modal-OPENAPI").click();
    await page.locator("#next-wizard-page").click();

    await page.getByTestId("create-artifact-modal-artifact-metadata-name").fill("Test Artifact");
    await page.locator("#next-wizard-page").click();

    await page.getByTestId("create-artifact-modal-version").fill(TEST_VERSION);
    await page.locator("#artifact-content").fill(OPENAPI_DATA_STR);
    await page.locator("#next-wizard-page").click();
    await page.locator("#next-wizard-page").click();

    await expect(page).toHaveURL(new RegExp(`.+/explore/${TEST_GROUP}/${TEST_ARTIFACT}`));

    // Step 3: Navigate to the version page
    await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/${TEST_ARTIFACT}/versions/${TEST_VERSION}`);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    // Step 4: Click on the References tab
    await page.getByTestId("version-references-tab").click();

    // Step 5: Verify the view mode toggle is visible with List and Graph options
    const viewModeToggle = page.getByTestId("view-mode-toggle");
    await expect(viewModeToggle).toBeVisible();
    await expect(page.getByTestId("view-mode-list")).toBeVisible();
    await expect(page.getByTestId("view-mode-graph")).toBeVisible();

    // Step 6: Verify List view is selected by default
    const listButton = page.getByTestId("view-mode-list").locator("button");
    const graphButton = page.getByTestId("view-mode-graph").locator("button");
    await expect(listButton).toHaveAttribute("aria-pressed", "true");
    await expect(graphButton).toHaveAttribute("aria-pressed", "false");

    // Step 7: Switch to Graph view
    await page.getByTestId("view-mode-graph").click();
    await expect(graphButton).toHaveAttribute("aria-pressed", "true");
    await expect(listButton).toHaveAttribute("aria-pressed", "false");

    // Step 8: Verify graph elements are visible
    await expect(page.getByTestId("reference-graph-wrapper")).toBeVisible();

    // Step 9: Verify empty state is shown (no references in our test artifact)
    await expect(page.getByTestId("graph-empty-state")).toBeVisible();

    // Step 10: Verify reference type toggle is visible
    await expect(page.getByTestId("reference-type-toggle")).toBeVisible();

    // Step 11: Switch back to List view
    await page.getByTestId("view-mode-list").click();
    await expect(listButton).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByTestId("reference-graph-wrapper")).not.toBeVisible();

    // Cleanup is skipped because:
    // 1. The test uses unique group names (timestamp-based) to avoid conflicts
    // 2. CI environments start fresh each run
    // 3. Core Reference Graph functionality has been verified
});
