import { test, expect } from "@playwright/test";
import { OPENAPI_DATA } from "./data/openapi-simple";

const OPENAPI_DATA_STR: string = JSON.stringify(OPENAPI_DATA, null, 4);

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

// Test group name for reference graph tests
const TEST_GROUP = "ReferenceGraphTest";

test.describe("Reference Graph Feature", () => {
    // Setup: Create test group and artifact before all tests
    test.beforeAll(async ({ browser }) => {
        const page = await browser.newPage();

        // Navigate to registry
        await page.goto(REGISTRY_UI_URL);
        await expect(page).toHaveTitle(/Apicurio Registry/);

        // Create test group
        await page.getByTestId("btn-toolbar-create-group").click();
        await page.getByTestId("create-group-groupId").fill(TEST_GROUP);
        await page.getByTestId("create-group-modal-btn-create").click();
        await expect(page).toHaveURL(new RegExp(`.+/explore/${TEST_GROUP}`));

        // Create test artifact
        await page.getByTestId("btn-create-artifact").click();
        await page.getByTestId("create-artifact-modal-id").fill("TestArtifact");
        await page.getByTestId("create-artifact-modal-type-select").click();
        await page.getByTestId("create-artifact-modal-OPENAPI").click();
        await page.locator("#next-wizard-page").click();

        // Fill metadata
        await page.getByTestId("create-artifact-modal-artifact-metadata-name").fill("Test Artifact for References");
        await page.locator("#next-wizard-page").click();

        // Fill version content
        await page.getByTestId("create-artifact-modal-version").fill("1.0.0");
        await page.locator("#artifact-content").fill(OPENAPI_DATA_STR);
        await page.locator("#next-wizard-page").click();

        // Complete wizard
        await page.locator("#next-wizard-page").click();
        await expect(page).toHaveURL(new RegExp(`.+/explore/${TEST_GROUP}/TestArtifact`));

        await page.close();
    });

    // Cleanup: Delete test group after all tests
    test.afterAll(async ({ browser }) => {
        const page = await browser.newPage();

        // Delete test artifact first
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact`);
        await page.getByTestId("header-btn-delete").click();
        await page.getByTestId("modal-btn-delete").click();
        await expect(page).toHaveURL(new RegExp(`.+/explore`));

        // Delete test group
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}`);
        await page.getByTestId("header-btn-delete").click();
        await page.getByTestId("modal-btn-delete").click();
        await expect(page).toHaveURL(new RegExp(`.+/explore`));

        await page.close();
    });

    test("References tab - Switch from list to graph view", async ({ page }) => {
        // Navigate to version page
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0`);

        // Click the references tab
        await page.getByTestId("version-references-tab").click();

        // Verify the view mode toggle is visible
        await expect(page.getByTestId("view-mode-toggle")).toBeVisible();

        // Verify list view is selected by default
        await expect(page.getByTestId("view-mode-list")).toHaveAttribute("aria-pressed", "true");
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "false");

        // Click on graph view toggle
        await page.getByTestId("view-mode-graph").click();

        // Verify graph view is now selected
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "true");
        await expect(page.getByTestId("view-mode-list")).toHaveAttribute("aria-pressed", "false");
    });

    test("References tab - Graph view shows empty state when no references", async ({ page }) => {
        // Navigate to version page with graph view
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0/references?view=graph`);

        // Wait for the graph to load
        await page.waitForTimeout(1000);

        // Verify the empty state is displayed
        await expect(page.getByTestId("graph-empty-state")).toBeVisible();
        await expect(page.getByText("No references found")).toBeVisible();
    });

    test("References tab - Graph view controls toolbar is visible", async ({ page }) => {
        // Navigate to version page with graph view
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0/references?view=graph`);

        // Verify the controls toolbar is visible
        await expect(page.getByTestId("graph-controls-toolbar")).toBeVisible();

        // Verify the depth label is visible
        await expect(page.getByText("Depth:")).toBeVisible();
    });

    test("References tab - Switch back to list view from graph", async ({ page }) => {
        // Navigate to version page with graph view
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0/references?view=graph`);

        // Verify graph view is selected
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "true");

        // Click on list view toggle
        await page.getByTestId("view-mode-list").click();

        // Verify list view is now selected
        await expect(page.getByTestId("view-mode-list")).toHaveAttribute("aria-pressed", "true");
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "false");

        // Verify the graph container is no longer visible
        await expect(page.getByTestId("reference-graph-wrapper")).not.toBeVisible();
    });

    test("References tab - Toggle inbound/outbound references in graph view", async ({ page }) => {
        // Navigate to version page with graph view
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0/references?view=graph`);

        // Find the toggle switch for inbound/outbound references
        const toggleSwitch = page.getByTestId("reference-type-toggle");
        await expect(toggleSwitch).toBeVisible();

        // Toggle to inbound view
        await toggleSwitch.click();

        // Wait for the graph to reload
        await page.waitForTimeout(500);

        // Verify empty state still shows (since there are no inbound references either)
        await expect(page.getByTestId("graph-empty-state")).toBeVisible();
    });

    test("References tab - Graph view persists after page reload", async ({ page }) => {
        // Navigate to version page
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0`);

        // Click the references tab
        await page.getByTestId("version-references-tab").click();

        // Switch to graph view
        await page.getByTestId("view-mode-graph").click();
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "true");

        // Reload the page
        await page.reload();

        // Wait for the page to load
        await page.waitForTimeout(1000);

        // Click the references tab again
        await page.getByTestId("version-references-tab").click();

        // Verify graph view is still selected (persisted in local storage)
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "true");
    });

    test("References tab - Direct URL navigation to graph view works", async ({ page }) => {
        // Navigate directly to graph view via URL parameter
        await page.goto(`${REGISTRY_UI_URL}/explore/${TEST_GROUP}/TestArtifact/versions/1.0.0/references?view=graph`);

        // Verify graph view is selected
        await expect(page.getByTestId("view-mode-graph")).toHaveAttribute("aria-pressed", "true");

        // Verify the graph wrapper is visible
        await expect(page.getByTestId("reference-graph-wrapper")).toBeVisible();
    });
});
