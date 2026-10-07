import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

test.describe.configure({ mode: "parallel" });

test.describe("Global Rules", () => {
    test.beforeEach(async ({ page }) => {
        await page.goto(REGISTRY_UI_URL);
        await expect(page).toHaveTitle(/Apicurio Registry/);
        await page.getByTestId("rules-tab").click();
    });

    test("Global Rules - List Rules", async ({ page }) => {
        await expect(page.locator("div.rule")).toHaveCount(3);
        await expect(page.locator("#validity-rule-name")).toContainText("Validity rule");
        await expect(page.locator("#compatibility-rule-name")).toContainText("Compatibility rule");
        await expect(page.locator("#integrity-rule-name")).toContainText("Integrity rule");
    });

    test("Global Rules - Enable Validity Rule", async ({ page }) => {
        await expect(page.locator("#validity-rule-name")).toContainText("Validity rule");

        // Enable the Rule
        await page.getByTestId("rules-validity-enable").click();
        expect(page.getByTestId("rules-validity-config-toggle")).toBeDefined();

        // Click the Rule Configuration toggle
        await page.getByTestId("rules-validity-config-toggle").click();
        expect(page.getByTestId("rules-validity-config-syntaxOnly")).toBeDefined();

        // Select "syntax only" config option
        await page.getByTestId("validity-config-syntax").click();
        expect(page.getByTestId("rules-validity-disable")).toBeDefined();

        // Disable the rule (back to original state)
        await page.getByTestId("rules-validity-disable").click();
    });

    test("Global Rules - Enable Compatibility Rule", async ({ page }) => {
        await expect(page.locator("#compatibility-rule-name")).toContainText("Compatibility rule");

        // Enable the Rule
        await page.getByTestId("rules-compatibility-enable").click();
        expect(page.getByTestId("rules-compatibility-config-toggle")).toBeDefined();

        // Click the Rule Configuration toggle
        await page.getByTestId("rules-compatibility-config-toggle").click();
        expect(page.getByTestId("rules-compatibility-config-forward")).toBeDefined();

        // Select "forward" config option
        await page.getByTestId("compatibility-config-forward").click();
        expect(page.getByTestId("rules-compatibility-disable")).toBeDefined();

        // Disable the rule (back to original state)
        await page.getByTestId("rules-compatibility-disable").click();
    });

    test("Global Rules - Enable Integrity Rule", async ({ page }) => {
        await expect(page.locator("#integrity-rule-name")).toContainText("Integrity rule");

        // Enable the Rule
        await page.getByTestId("rules-integrity-enable").click();
        expect(page.getByTestId("rules-integrity-config-toggle")).toBeDefined();

        // Click the Rule Configuration toggle
        await page.getByTestId("rules-integrity-config-toggle").click();
        expect(page.getByTestId("integrity-config-full")).toBeDefined();

        // Select "none" config option
        await page.getByTestId("integrity-config-none").locator("input").click();
        expect(page.getByTestId("rules-integrity-disable")).toBeDefined();

        // Disable the rule (back to original state)
        await page.getByTestId("rules-integrity-disable").click();
    });
});

test("Global Rules - Enable Validity Rule failure keeps the page intact", async ({ page }) => {
    // Force the rule-creation request to fail, simulating a backend/network error.
    await page.route("**/apis/registry/v3/admin/rules", async route => {
        if (route.request().method() === "POST") {
            await route.fulfill({
                status: 500,
                contentType: "application/json",
                body: JSON.stringify({ detail: "Simulated failure" })
            });
        } else {
            await route.continue();
        }
    });

    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await page.getByTestId("rules-tab").click();

    await expect(page.locator("#validity-rule-name")).toContainText("Validity rule");

    // Attempt to enable the rule; the mocked request fails.
    await page.getByTestId("rules-validity-enable").click();

    // The page must remain intact - no full-page error, the other rules are still there.
    await expect(page.locator("div.rule")).toHaveCount(3);
    await expect(page.getByTestId("rule-action-error")).toContainText("Simulated failure");

    // The rule must NOT show as enabled - the failed request must not have been applied optimistically.
    await expect(page.getByTestId("rules-validity-enable")).toBeVisible();
    await expect(page.getByTestId("rules-validity-disable")).toHaveCount(0);
});
