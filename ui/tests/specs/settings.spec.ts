import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";


test.beforeEach(async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await page.getByTestId("settings-tab").click();
});

test("Settings - Filter", async ({ page }) => {
    await expect(page.getByTestId("settings-search-widget").locator("input")).toBeEmpty();
    expect(page.getByTestId("config-groups")).toBeDefined();
    await expect(page.getByTestId("config-groups").locator(".configuration-property")).toHaveCount(8);

    await page.getByTestId("settings-search-widget").locator("input").fill("legacy");
    await expect(page.getByTestId("settings-search-widget").locator("input")).toHaveValue("legacy");
    await page.getByTestId("settings-search-widget").locator("button[type=submit]").click();

    await expect(page.getByTestId("config-groups").locator(".configuration-property")).toHaveCount(1);
});
