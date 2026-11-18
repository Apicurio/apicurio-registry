import { test, expect, Locator, Page } from "@playwright/test";

const REGISTRY_UI_URL: string = process.env["REGISTRY_UI_URL"] || "http://localhost:8888";
const REGISTRY_API_URL: string = process.env["REGISTRY_API_URL"] || "http://localhost:8080/apis/registry/v3";

const GROUP_ID: string = "OverviewDrawerTest";
const ARTIFACT_ID: string = "drawer-artifact";
const VERSION: string = "1.0.0";
const COMMENT: string = "Overview drawer layout comment";

// Below the stacking breakpoint (1024px), above it, and inside the 800-1040px range from issue #11112.
const WIDTHS: number[] = [760, 900, 1000, 1100];

test.beforeAll(async ({ request }) => {
    const artifactUrl: string = `${REGISTRY_API_URL}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}`;
    if ((await request.get(artifactUrl)).status() === 404) {
        const created = await request.post(`${REGISTRY_API_URL}/groups/${GROUP_ID}/artifacts`, {
            data: {
                artifactId: ARTIFACT_ID,
                artifactType: "JSON",
                firstVersion: {
                    version: VERSION,
                    description: "A long description used to check that the versions table does not collapse",
                    content: { content: "{\"type\":\"object\"}", contentType: "application/json" }
                }
            }
        });
        expect(created.status()).toBe(200);
        const comment = await request.post(`${artifactUrl}/versions/${VERSION}/comments`, { data: { value: COMMENT } });
        expect(comment.status()).toBe(200);
    }
});

/**
 * Scrolls the element into view and asserts that it lies horizontally within the viewport and has a usable (non-collapsed) width.
 */
const expectOnScreen = async (page: Page, locator: Locator, minWidth: number): Promise<void> => {
    await expect(locator).toBeVisible();
    await locator.scrollIntoViewIfNeeded();
    const box = await locator.boundingBox();
    const viewportWidth: number = page.viewportSize()!.width;
    expect(box).not.toBeNull();
    expect(box!.width).toBeGreaterThanOrEqual(minWidth);
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(viewportWidth);
};

for (const width of WIDTHS) {
    test.describe(`Overview drawer at ${width}px`, () => {
        test.use({ viewport: { width, height: 800 } });

        test("Artifact overview shows the versions table", async ({ page }) => {
            await page.goto(`${REGISTRY_UI_URL}/explore/${GROUP_ID}/${ARTIFACT_ID}`);
            const versionLink: Locator = page.locator(".versions-table tbody").getByRole("link", { name: VERSION });
            await expectOnScreen(page, versionLink, 20);
            await expectOnScreen(page, page.locator(".versions-table tbody tr").first(), 200);
        });

        test("Group overview shows the artifacts table", async ({ page }) => {
            await page.goto(`${REGISTRY_UI_URL}/explore/${GROUP_ID}`);
            const artifactLink: Locator = page.locator(".artifacts-table tbody").getByRole("link", { name: ARTIFACT_ID });
            await expectOnScreen(page, artifactLink, 20);
            await expectOnScreen(page, page.locator(".artifacts-table tbody tr").first(), 200);
        });

        test("Version overview shows the comments", async ({ page }) => {
            await page.goto(`${REGISTRY_UI_URL}/explore/${GROUP_ID}/${ARTIFACT_ID}/versions/${VERSION}`);
            const comment: Locator = page.locator(".comments").getByText(COMMENT);
            await expectOnScreen(page, comment, 100);
        });
    });
}
