import { test, expect, Page, Route } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";

const ARTIFACT_PATH = "/apis/registry/v3/groups/default/artifacts/test-artifact";

const SUBMITTED = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"},{\"name\":\"b\",\"type\":\"string\"}]}";

const json = (route: Route, status: number, body: unknown) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

/**
 * Mocks every registry call the artifact, group, search and dashboard pages make, and rejects a new
 * artifact or a new version with the given rule violation body.
 */
const EXISTING = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]}";

const mockRegistry = async (page: Page, violation: unknown, status: number = 400): Promise<void> => {
    await page.route("**/apis/registry/v3/**", async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path.endsWith("/system/uiConfig")) {
            return json(route, 200, {
                ui: { contextPath: "/", navPrefixPath: "/" },
                auth: { type: "none", rbacEnabled: false, obacEnabled: false },
                features: { readOnly: false, breadcrumbs: true }
            });
        }
        if (path === ARTIFACT_PATH) {
            return json(route, 200, { artifactId: "test-artifact", artifactType: "AVRO", owner: "", createdOn: "2026-10-01T00:00:00Z" });
        }
        if (path === `${ARTIFACT_PATH}/rules`) {
            return json(route, 200, ["COMPATIBILITY"]);
        }
        if (path === `${ARTIFACT_PATH}/rules/COMPATIBILITY`) {
            return json(route, 200, { ruleType: "COMPATIBILITY", config: "BACKWARD" });
        }
        if (path === `${ARTIFACT_PATH}/versions/1`) {
            return json(route, 200, { artifactId: "test-artifact", artifactType: "AVRO", version: "1", state: "ENABLED", globalId: 1, contentId: 1 });
        }
        if (path === `${ARTIFACT_PATH}/versions/1/content`) {
            return route.fulfill({ status: 200, contentType: "application/json", body: EXISTING });
        }
        if (path === `${ARTIFACT_PATH}/versions/1/comments`) {
            return json(route, 200, []);
        }
        if (path.endsWith("/admin/config/artifactTypes")) {
            return json(route, 200, [{ name: "AVRO" }, { name: "JSON" }]);
        }
        if (path.endsWith("/search/artifacts") || path.endsWith("/search/groups")) {
            return json(route, 200, { count: 0, artifacts: [], groups: [] });
        }
        if (path.endsWith("/search/versions")) {
            return json(route, 200, {
                count: 1,
                versions: [{ artifactId: "test-artifact", artifactType: "AVRO", version: "1", state: "ENABLED", globalId: 1, contentId: 1 }]
            });
        }
        if (request.method() === "POST"
            && (path === `${ARTIFACT_PATH}/versions` || path === "/apis/registry/v3/groups/default/artifacts")) {
            return json(route, status, violation);
        }
        return json(route, 404, { status: 404, title: "Not mocked", name: "NotFoundException" });
    });
};

const submitNewVersion = async (page: Page): Promise<void> => {
    await page.goto(`${REGISTRY_UI_URL}/explore/default/test-artifact`);
    await expect(page).toHaveTitle(/Apicurio Registry/);
    await page.getByTestId("btn-create-version").click();
    await page.getByTestId("form-version-content").locator("textarea").fill(SUBMITTED);
    await page.getByTestId("modal-btn-create").click();
};

/** Walks the create artifact wizard: coordinates, metadata, content, references, version metadata. */
const submitNewArtifact = async (page: Page): Promise<void> => {
    await page.getByTestId("create-artifact-modal-id").fill("new-artifact");
    const next = page.getByRole("button", { name: "Next" });
    await next.click();
    await next.click();
    await page.getByTestId("create-artifact-modal-file-upload").locator("textarea").fill(SUBMITTED);
    await next.click();
    await next.click();
    await page.getByRole("button", { name: "Create" }).click();
};

const ARTIFACT_VIOLATION = {
    status: 400,
    title: "Incompatible artifact: new-artifact [AVRO]",
    name: "RuleViolationException",
    causes: [{ description: "b", context: "/fields/1" }]
};

/** The report shows the cause and the content that was typed into the wizard. */
const expectReportWithSubmittedContent = async (page: Page): Promise<void> => {
    const groups = page.getByTestId("compatibility-report-group");
    await expect(groups).toHaveCount(1);
    await expect(groups.first().locator("b")).toHaveText("/fields/1");
    await expect(page.getByTestId("compatibility-report-content")).toContainText("\"name\":\"User\"");
};

const CREATE_ARTIFACT_ENTRY_POINTS = [
    { page: "Dashboard", path: "/dashboard", button: "quick-action-create" },
    { page: "Search", path: "/search", button: "empty-btn-create" },
    { page: "Group", path: "/explore/default", button: "btn-create-artifact" }
];

for (const entry of CREATE_ARTIFACT_ENTRY_POINTS) {
    test(`Compatibility report - a rejected new artifact from the ${entry.page} page shows the causes and the submitted content`, async ({ page }) => {
        await mockRegistry(page, ARTIFACT_VIOLATION);

        await page.goto(`${REGISTRY_UI_URL}${entry.path}`);
        await page.getByTestId(entry.button).click();
        await submitNewArtifact(page);

        await expectReportWithSubmittedContent(page);
    });
}

const CAUSES = [
    {
        description: "Field 'b' was added without a default value.",
        context: "/fields/1",
        direction: "BACKWARD",
        proposed: { pointer: "/fields/1" },
        existing: { pointer: "/fields", version: "1" }
    },
    {
        description: "The reader schema has no default for 'b'.",
        context: "/fields/1/type",
        direction: "BACKWARD",
        proposed: { pointer: "/fields/1" },
        existing: { pointer: "/fields", version: "1" }
    }
];

test("Compatibility report - a rejected new version lists the causes and shows the submitted content", async ({ page }) => {
    await mockRegistry(page, {
        status: 400,
        title: "Incompatible artifact: test-artifact [AVRO]",
        name: "RuleViolationException",
        // During 3.4 the server sends the causes in both places (#10518).
        ruleViolation: { ruleType: "COMPATIBILITY", causes: CAUSES },
        causes: CAUSES
    });

    await submitNewVersion(page);

    const report = page.getByTestId("compatibility-report");
    await expect(report).toBeVisible();

    // Both causes share a location, so they are listed under one group.
    const groups = report.getByTestId("compatibility-report-group");
    await expect(groups).toHaveCount(1);
    await expect(groups.first()).toContainText("/fields/1");
    await expect(groups.first()).toContainText("Field 'b' was added without a default value.");
    await expect(groups.first()).toContainText("The reader schema has no default for 'b'.");
    await expect(groups.first()).toContainText("Breaks backward compatibility");

    // The content the user submitted is still there to look at.
    const content = report.getByTestId("compatibility-report-content");
    await expect(content).toContainText("Submitted content");
    await expect(content).toContainText("\"name\":\"b\"");
    await expect(report.getByTestId("compatibility-report-compare")).toHaveText("Compare with version 1");

    // The version the causes were found against can be put next to the submitted content.
    await report.getByTestId("compatibility-report-compare").click();
    await expect(content).toContainText("Submitted content compared with version 1");
    await expect(report.getByTestId("compatibility-report-compare")).toHaveText("Show submitted content only");
});

test("Compatibility report - testing content from the version page shows the causes and the tested content", async ({ page }) => {
    await mockRegistry(page, ARTIFACT_VIOLATION);

    await page.goto(`${REGISTRY_UI_URL}/explore/default/test-artifact/versions/1`);
    await page.getByTestId("version-actions-toggle").click();
    await page.getByTestId("action-test-version").click();
    await page.getByTestId("form-test-version-content").locator("textarea").fill(SUBMITTED);
    await page.getByTestId("modal-btn-test").click();

    await expectReportWithSubmittedContent(page);
});

test("Compatibility report - a version that passes the rules still opens the new version, with no report", async ({ page }) => {
    await mockRegistry(page, {
        groupId: "default", artifactId: "test-artifact", artifactType: "AVRO", version: "1", state: "ENABLED", globalId: 1, contentId: 1
    }, 200);

    await submitNewVersion(page);

    await expect(page).toHaveURL(/\/explore\/default\/test-artifact\/versions\/1$/);
    await expect(page.getByTestId("compatibility-report")).toHaveCount(0);
});

test("Compatibility report - an error that is not a rule violation still shows the page error", async ({ page }) => {
    await mockRegistry(page, {
        status: 409,
        title: "An artifact with ID 'new-artifact' in group 'default' already exists.",
        name: "ArtifactAlreadyExistsException"
    }, 409);

    await page.goto(`${REGISTRY_UI_URL}/dashboard`);
    await page.getByTestId("quick-action-create").click();
    await submitNewArtifact(page);

    await expect(page.getByText("Error creating artifact.")).toBeVisible();
    await expect(page.getByTestId("compatibility-report")).toHaveCount(0);
});

test("Compatibility report - the 3.5 body, with causes only under ruleViolation, still shows the report", async ({ page }) => {
    await mockRegistry(page, {
        status: 400,
        title: "Incompatible artifact: test-artifact [AVRO]",
        ruleViolation: { ruleType: "COMPATIBILITY", causes: [{ description: "b", context: "/fields/1" }] }
    });

    await submitNewVersion(page);

    const groups = page.getByTestId("compatibility-report-group");
    await expect(groups).toHaveCount(1);
    await expect(groups.first().locator("b")).toHaveText("/fields/1");
});

test("Compatibility report - without the new fields it shows the same rows as before", async ({ page }) => {
    // The body a 3.3 server sends: only description and context, no ruleType.
    await mockRegistry(page, {
        status: 400,
        title: "Incompatible artifact: test-artifact [AVRO]",
        name: "RuleViolationException",
        causes: [{ description: "b", context: "/fields/1" }]
    });

    await submitNewVersion(page);

    const groups = page.getByTestId("compatibility-report-group");
    await expect(groups).toHaveCount(1);
    await expect(groups.first().locator("b")).toHaveText("/fields/1");
    await expect(groups.first()).toContainText("b");
    await expect(page.getByTestId("compatibility-report-compare")).toHaveCount(0);
    await expect(page.getByTestId("compatibility-report-content")).toContainText("\"name\":\"User\"");
});
