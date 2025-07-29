import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888/";
const EXPECTED_TITLE: string = process.env["EXPECTED_TITLE"] || "Apicurio Registry";
const EXPECTED_LOGO_SRC: string = process.env["EXPECTED_LOGO_SRC"] || "/apicurio_registry_logo_reverse.svg";

test("Masthead - Logo verification", async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);

    // Wait for the page to load by checking the title
    await expect(page).toHaveTitle(/Apicurio Registry/);

    // Locate the masthead logo image
    const logoImage = page.locator(".pf-v5-c-masthead__brand img.pf-v5-c-brand");

    // Wait for the logo image to be visible
    await expect(logoImage).toBeVisible();

    // Verify the src attribute
    await expect(logoImage).toHaveAttribute("src", EXPECTED_LOGO_SRC);

    // Verify the alt text
    await expect(logoImage).toHaveAttribute("alt", EXPECTED_TITLE);
});
