import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";


test.beforeEach(async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await page.getByTestId("settings-tab").click();
});

test("Settings - Filter", async ({ page }) => {
    const searchWidget = page.getByTestId("settings-search-widget");
    const searchInput = searchWidget.locator("input");
    const configGroups = page.getByTestId("config-groups");
    const properties = configGroups.locator(".configuration-property");

    await expect(searchInput).toBeEmpty();
    await expect(configGroups).toBeVisible();
    await expect(properties.first()).toBeVisible();

    await searchInput.fill("legacy");
    await expect(searchInput).toHaveValue("legacy");
    await searchWidget.locator("button[type=submit]").click();

    await expect(properties).toHaveCount(1);
    await expect(properties.locator(".property-name .name"))
        .toHaveText("Legacy ID mode (compatibility API)");
});
