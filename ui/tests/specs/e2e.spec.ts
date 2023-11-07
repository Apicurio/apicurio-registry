import { test, expect } from "@playwright/test";
import { OPENAPI_DATA } from "./data/openapi-simple";
import { OPENAPI_DATA_V2 } from "./data/openapi-simple-v2";

const OPENAPI_DATA_STR: string = JSON.stringify(OPENAPI_DATA, null, 4);
const OPENAPI_DATA_V2_STR: string = JSON.stringify(OPENAPI_DATA_V2, null, 4);

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

test("End to End - Upload artifact", async ({ page }) => {
    await page.goto(REGISTRY_UI_URL);
    await expect(page).toHaveTitle(/Apicurio Registry/);

    expect(page.getByTestId("btn-toolbar-upload-artifact")).toBeDefined();

    // Click the "Upload artifact" button
    await page.getByTestId("btn-toolbar-upload-artifact").click();
    await expect(page.getByTestId("upload-artifact-form-group")).toHaveValue("");

    // Upload a new artifact
    await page.getByTestId("upload-artifact-form-group").fill("e2e");
    await page.getByTestId("upload-artifact-form-id").fill("MyArtifact");
    await page.getByTestId("upload-artifact-form-type-select").click();
    await page.getByTestId("upload-artifact-form-OPENAPI").click();
    await page.locator("#artifact-content").fill(OPENAPI_DATA_STR);
    await page.getByTestId("upload-artifact-modal-btn-upload").click();

    // Make sure we redirected to the artifact detail page.
    await expect(page).toHaveURL(/.+\/artifacts\/e2e\/MyArtifact\/versions\/latest/);

    // Assert the meta-data is as expected
    await expect(page.getByTestId("artifact-details-name")).toHaveText("Empty API Spec");
    await expect(page.getByTestId("artifact-details-id")).toHaveText("MyArtifact");
    await expect(page.getByTestId("artifact-details-state")).toHaveText("ENABLED");
    await expect(page.getByTestId("artifact-details-labels")).toHaveText("No labels");
    await expect(page.getByTestId("artifact-details-properties")).toHaveText("No properties");
});


test("End to End - Edit metadata", async ({ page }) => {
    // Navigate to the artifact details page
    await page.goto(`${REGISTRY_UI_URL}/artifacts/e2e/MyArtifact/versions/latest`);

    // Click the "Edit" button to show the modal
    await page.getByTestId("artifact-btn-edit").click();
    await expect(page.getByTestId("edit-metadata-modal-name")).toHaveValue("Empty API Spec");

    // Change/add some values
    await page.getByTestId("edit-metadata-modal-name").fill("My Empty API");
    await page.getByTestId("edit-metadata-modal-description").fill("A simple empty API.");
    await page.getByTestId("edit-metadata-modal-labels").fill("one, two, three");

    // Add a property
    await page.getByTestId("edit-metadata-modal-add-property").click();
    await page.getByTestId("edit-metadata-modal-property-name-0").fill("some-key");
    await page.getByTestId("edit-metadata-modal-property-value-0").fill("some-value");

    // Save changes
    await page.getByTestId("modal-btn-edit").click();

    // Assert the meta-data is as expected
    await expect(page.getByTestId("artifact-details-name")).toHaveText("My Empty API");
    await expect(page.getByTestId("artifact-details-description")).toHaveText("A simple empty API.");
    await expect(page.getByTestId("artifact-details-id")).toHaveText("MyArtifact");
    await expect(page.getByTestId("artifact-details-state")).toHaveText("ENABLED");
    expect(page.getByTestId("artifact-details-labels").getByText("one")).toBeDefined();
    expect(page.getByTestId("artifact-details-labels").getByText("two")).toBeDefined();
    expect(page.getByTestId("artifact-details-labels").getByText("three")).toBeDefined();
    expect(page.getByTestId("artifact-details-properties").getByText("some-key")).toBeDefined();
    expect(page.getByTestId("artifact-details-properties").getByText("some-value")).toBeDefined();
});


test("End to End - Artifact specific rules", async ({ page }) => {
    // Navigate to the artifact details page
    await page.goto(`${REGISTRY_UI_URL}/artifacts/e2e/MyArtifact/versions/latest`);

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


test("End to End - Upload new version", async ({ page }) => {
    // Navigate to the artifact details page
    await page.goto(`${REGISTRY_UI_URL}/artifacts/e2e/MyArtifact/versions/latest`);

    // Upload a new version
    await page.getByTestId("header-btn-upload-version").click();
    await page.locator("#artifact-content").fill(OPENAPI_DATA_V2_STR);
    await page.getByTestId("modal-btn-upload").click();

    // Make sure we redirected to the artifact detail page.
    await expect(page).toHaveURL(/.+\/artifacts\/e2e\/MyArtifact\/versions\/2/);
});


test("End to End - Delete artifact", async ({ page }) => {
    await page.goto(`${REGISTRY_UI_URL}/artifacts/e2e/MyArtifact/versions/latest`);
    await page.getByTestId("header-btn-delete").click();
    await page.getByTestId("modal-btn-delete").click();

    await expect(page).toHaveURL(/.+\/artifacts/);
});
