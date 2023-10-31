import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";


test.beforeEach(async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await page.getByTestId("rules-tab").click();
});


test("Global Rules - List Rules", async ({ page }) => {
    await expect(page.locator("#validity-rule-name")).toContainText("Validity rule");
    await expect(page.locator("#compatibility-rule-name")).toContainText("Compatibility rule");
    await expect(page.locator("#integrity-rule-name")).toContainText("Integrity rule");
});
