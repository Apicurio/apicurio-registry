import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888/";

test.beforeEach(async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await page.getByTestId("search-tab").click();
});

test("Search - Empty state has Create Artifact button for ARTIFACT, not for GROUP or VERSION", async ({ page }) => {
    // 1. By default, Search Type is "Artifacts" and search list is empty.
    // Verify that the "Create artifact" button is visible in empty state.
    await expect(page.getByTestId("empty-btn-create")).toBeVisible();

    // 2. Click the "Create artifact" button and verify it opens the Create Artifact modal.
    await page.getByTestId("empty-btn-create").click();
    await expect(page.getByTestId("create-artifact-modal-id")).toBeVisible();

    // Close the modal
    await page.getByRole("button", { name: "Close" }).first().click();
    await expect(page.getByTestId("create-artifact-modal-id")).toBeHidden();

    // 3. Switch search type to "Groups"
    await page.getByTestId("search-type-select").click();
    await page.getByTestId("search-type-groups").click();

    // Verify that the "Create artifact" button is NOT visible.
    await expect(page.getByTestId("empty-btn-create")).toBeHidden();

    // 4. Switch search type to "Versions"
    await page.getByTestId("search-type-select").click();
    await page.getByTestId("search-type-versions").click();

    // Verify that the "Create artifact" button is NOT visible.
    await expect(page.getByTestId("empty-btn-create")).toBeHidden();
});
