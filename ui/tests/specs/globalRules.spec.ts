import { test, expect } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

test.describe.configure({ mode: "parallel" });

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
