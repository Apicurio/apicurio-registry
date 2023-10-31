import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8081/";

test("App - Has Title", async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);

    // Expect a title "to contain" a substring.
    await expect(page).toHaveTitle(/Apicurio Registry/);
});
