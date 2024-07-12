import { test, expect } from "@playwright/test";
import { OPENAPI_DATA } from "./data/openapi-simple";
import { OPENAPI_DATA_V2 } from "./data/openapi-simple-v2";

const OPENAPI_DATA_STR: string = JSON.stringify(OPENAPI_DATA, null, 4);
const OPENAPI_DATA_V2_STR: string = JSON.stringify(OPENAPI_DATA_V2, null, 4);

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

test("End to End - Create artifact", async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    expect(page.getByTestId("btn-toolbar-create-artifact")).toBeDefined();

    // Click the "Create artifact" button
    await page.getByTestId("btn-toolbar-create-artifact").click();
    await expect(page.getByTestId("create-artifact-modal-group")).toHaveValue("");

    // Create a new artifact

    // Fill out page 1 of the create artifact wizard
    await page.getByTestId("create-artifact-modal-group").fill("e2e");
    await page.getByTestId("create-artifact-modal-id").fill("MyArtifact");
    await page.getByTestId("create-artifact-modal-type-select").click();
    await page.getByTestId("create-artifact-modal-OPENAPI").click();

    // Click "Next" on the wizard
    await page.locator("#next-wizard-page").click();

    // Fill out page 2 of the create artifact wizard
    await page.getByTestId("create-artifact-modal-artifact-metadata-name").fill("Test Artifact");
    await page.getByTestId("create-artifact-modal-artifact-metadata-description").fill("Artifact description.");

    // Click "Next" on the wizard
    await page.locator("#next-wizard-page").click();

    // Fill out page 3 of the create artifact wizard
    await page.getByTestId("create-artifact-modal-version").fill("1.0.0");
    await page.locator("#artifact-content").fill(OPENAPI_DATA_STR);

    // Click "Next" on the wizard
    await page.locator("#next-wizard-page").click();

    // Leave page 4 empty and click "Complete"
    await page.locator("#next-wizard-page").click();

    // Make sure we redirected to the artifact page.
    await expect(page).toHaveURL(/.+\/explore\/e2e\/MyArtifact/);

    // Assert the meta-data is as expected
    await expect(page.getByTestId("artifact-details-name")).toHaveText("Test Artifact");
    await expect(page.getByTestId("artifact-details-description")).toHaveText("Artifact description.");
    await expect(page.getByTestId("artifact-details-labels")).toHaveText("No labels");
});


test("End to End - Edit artifact metadata", async ({ page }) => {
    // Navigate to the artifact details page
    await page.goto(`${REGISTRY_UI_URL}/explore/e2e/MyArtifact`);

    // Click the "Edit" button to show the modal
    await page.getByTestId("artifact-btn-edit").click();
    await expect(page.getByTestId("edit-metadata-modal-name")).toHaveValue("Test Artifact");

    // Change/add some values
    await page.getByTestId("edit-metadata-modal-name").fill("Empty API Spec");
    await page.getByTestId("edit-metadata-modal-description").fill("A simple empty API.");

    // Add a label
    await page.getByTestId("edit-metadata-modal-add-label").click();
    await page.getByTestId("edit-metadata-modal-label-name-0").fill("some-key");
    await page.getByTestId("edit-metadata-modal-label-value-0").fill("some-value");

    // Save changes
    await page.getByTestId("modal-btn-edit").click();

    // Wait
    await page.waitForTimeout(500);

    // Reload the page
    await page.reload();

    // Assert the meta-data is as expected
    await expect(page.getByTestId("artifact-details-name")).toHaveText("Empty API Spec");
    await expect(page.getByTestId("artifact-details-description")).toHaveText("A simple empty API.");
    expect(page.getByTestId("artifact-details-labels").getByText("some-key")).toBeDefined();
    expect(page.getByTestId("artifact-details-labels").getByText("some-value")).toBeDefined();
});



test("End to End - Artifact specific rules", async ({ page }) => {
    // Navigate to the artifact details page
    await page.goto(`${REGISTRY_UI_URL}/explore/e2e/MyArtifact`);

    await expect(page.locator("div.rule")).toHaveCount(3);
    await expect(page.locator("#validity-rule-name")).toContainText("Validity rule");
    await expect(page.locator("#compatibility-rule-name")).toContainText("Compatibility rule");
    await expect(page.locator("#integrity-rule-name")).toContainText("Integrity rule");

    // Enable the Rule
    await page.getByTestId("rules-validity-enable").click();
    expect(page.getByTestId("rules-validity-config-toggle")).toBeDefined();

    // Click the Rule Configuration toggle
    await page.getByTestId("rules-validity-config-toggle").click();
    expect(page.getByTestId("rules-validity-config-syntaxOnly")).toBeDefined();

    // Select "syntax only" config option
    await page.getByTestId("validity-config-syntax").click();
    expect(page.getByTestId("rules-validity-disable")).toBeDefined();
});


test("End to End - Create new version", async ({ page }) => {
    // Navigate to the artifact details page
    await page.goto(`${REGISTRY_UI_URL}/explore/e2e/MyArtifact`);

    // Click the "versions" tab
    await page.getByTestId("versions-tab").click();

    // Create a new version
    await page.getByTestId("btn-toolbar-create-version").click();
    await page.locator("#version-content").fill(OPENAPI_DATA_V2_STR);
    await page.getByTestId("modal-btn-create").click();

    // Make sure we redirected to the artifact detail page.
    await expect(page).toHaveURL(/.+\/explore\/e2e\/MyArtifact\/2/);
});


test("End to End - Delete artifact", async ({ page }) => {
    await page.goto(`${REGISTRY_UI_URL}/explore/e2e/MyArtifact`);
    await page.getByTestId("header-btn-delete").click();
    await page.getByTestId("modal-btn-delete").click();

    await expect(page).toHaveURL(/.+\/explore/);
});
