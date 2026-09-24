import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888/";
const NON_EXISTENT_SEARCH = "e2e-non-existent-artifact-9f8c7a2b";

test.beforeEach(async ({ page }) => {
    test.setTimeout(45000);
    let targetUrl = REGISTRY_UI_URL;

    if (!process.env.CI) {
        // Rewrite localhost to 127.0.0.1 to avoid macOS IPv6 connection resolution issues in local dev
        if (targetUrl.includes("localhost")) {
            targetUrl = targetUrl.replace("localhost", "127.0.0.1");
        }

        // Reroute backend API localhost requests to 127.0.0.1
        await page.route("**", async (route) => {
            const url = route.request().url();
            if (url.includes("localhost:8080")) {
                await route.continue({ url: url.replace("localhost:8080", "127.0.0.1:8080") });
            } else if (url.includes("localhost:8888")) {
                await route.continue({ url: url.replace("localhost:8888", "127.0.0.1:8888") });
            } else {
                await route.continue();
            }
        });
    }

    await page.goto(targetUrl);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    const responsePromise = page.waitForResponse(/\/search\/artifacts/);
    await page.getByTestId("search-tab").click();
    await responsePromise;
    await page.waitForTimeout(200);
});

test("Search - Empty state has Create Artifact button for ARTIFACT, not for GROUP or VERSION", async ({ page }) => {
    // 1. ARTIFACT Empty State
    // Explicitly perform a search using a stable value that is extremely unlikely to exist to trigger the empty state.
    let responsePromise = page.waitForResponse(/\/search\/artifacts/);
    await page.getByTestId("chip-filter-value").fill(NON_EXISTENT_SEARCH);
    await page.getByTestId("chip-filter-search").click();
    await responsePromise;
    await page.waitForTimeout(200);

    // Verify empty state text
    await expect(page.getByText("No artifacts found")).toBeVisible();

    // Verify Create Artifact button is visible
    await expect(page.getByTestId("empty-btn-create")).toBeVisible();

    // Verify clicking it opens the modal
    await page.getByTestId("empty-btn-create").click();
    await expect(page.getByTestId("create-artifact-modal-id")).toBeVisible();

    // Close the modal
    await page.getByRole("button", { name: "Close" }).first().click();
    await expect(page.getByTestId("create-artifact-modal-id")).toBeHidden();

    // 2. GROUP Empty State
    responsePromise = page.waitForResponse(/\/search\/groups/);
    await page.getByTestId("search-type-select").click();
    await page.getByTestId("search-type-groups").click();
    await responsePromise;
    await page.waitForTimeout(200);

    responsePromise = page.waitForResponse(/\/search\/groups/);
    await page.getByTestId("chip-filter-value").fill(NON_EXISTENT_SEARCH);
    await page.getByTestId("chip-filter-search").click();
    await responsePromise;
    await page.waitForTimeout(200);

    // Verify empty state text
    await expect(page.getByText("No groups found")).toBeVisible();

    // Verify Create Artifact button is not rendered
    await expect(page.getByTestId("empty-btn-create")).toHaveCount(0);

    // 3. VERSION Empty State
    responsePromise = page.waitForResponse(/\/search\/versions/);
    await page.getByTestId("search-type-select").click();
    await page.getByTestId("search-type-versions").click();
    await responsePromise;
    await page.waitForTimeout(200);

    responsePromise = page.waitForResponse(/\/search\/versions/);
    await page.getByTestId("chip-filter-value").fill(NON_EXISTENT_SEARCH);
    await page.getByTestId("chip-filter-search").click();
    await responsePromise;
    await page.waitForTimeout(200);

    // Verify empty state text
    await expect(page.getByText("No versions found")).toBeVisible();

    // Verify Create Artifact button is not rendered
    await expect(page.getByTestId("empty-btn-create")).toHaveCount(0);
});
