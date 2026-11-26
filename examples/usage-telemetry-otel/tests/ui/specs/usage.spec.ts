import { failContentIdQueries, ignoreContentIdSelection } from "../support/mutations.js";
import { filterPlan } from "../lib/plan.mjs";
import { expect, signIn, test } from "../support/fixtures.js";
import { errorFlow, filteredFlow, noMatchFlow, overviewFlow, sameNumberFlow, seeded } from "../support/flows.js";
import { attachShot } from "../support/shots.js";

test.describe("schema usage dashboard against real exported data", () => {
    test("form login: a wrong password is refused and the right one opens Grafana", async ({ page, ui, safeTrace }) => {
        await page.goto(`${ui.grafanaUrl}/login`);
        await page.getByLabel("Email or username").fill(ui.grafanaUser);
        await page.getByLabel("Password", { exact: true }).fill(`${ui.grafanaPassword}-wrong`);
        await page.getByRole("button", { name: "Log in" }).click();
        await expect(page.getByRole("alert")).toContainText(/invalid username or password/i);
        await expect(page).toHaveURL(/\/login/);

        await signIn(page, ui);
        // Tracing starts only now that the credentials were accepted, so what follows is traced and the form entry never is
        await safeTrace.start();
        await expect(page.getByRole("button", { name: "Profile" })).toBeVisible();
    });

    test("overview: exact rows and stats of the seeded run at its evaluation time", async ({ signedIn, ui }, testInfo) => {
        await overviewFlow(signedIn, seeded(ui), attachShot(signedIn, testInfo));
        expect(0, "REG501-INTENTIONAL-CI-FAILURE-7d98e01: deliberate negative control, not a defect; remove this line").toBe(1);
    });

    test("filters: client, identifier type, global ID and Content ID through the real controls", async ({ signedIn, ui }, testInfo) => {
        await filteredFlow(signedIn, seeded(ui), attachShot(signedIn, testInfo));
    });

    test("the same number as a global ID and a content ID stays separate when the run has one", async ({ signedIn, ui }, testInfo) => {
        const result = await sameNumberFlow(signedIn, seeded(ui));
        testInfo.annotations.push({ type: "coverage", description: `same-number isolation ${result}` });
    });

    test("no match shows no observations and no error marker", async ({ signedIn, ui }) => {
        await noMatchFlow(signedIn, seeded(ui));
    });

    test("a controlled datasource failure shows native error markers and the unknown presentation", async ({ signedIn, ui }) => {
        await errorFlow(signedIn, seeded(ui));
    });

    // A negative control, not a data check: from the moment the Content ID control is used, every query is widened so that its
    // Content ID condition ignores the control; the filter flow must fail at that step and not earlier
    test("mutation: a query that ignores the Content ID selection is caught at the Content ID step", async ({ signedIn, ui }) => {
        const trail: string[] = [];
        const mutation = ignoreContentIdSelection(signedIn, ui.grafanaUrl);
        const failure = await filteredFlow(signedIn, seeded(ui), async () => undefined, trail, mutation.start).then(
            () => undefined,
            (error: unknown) => error,
        );
        expect(failure, "the filter flow must fail while queries ignore the Content ID selection").toBeInstanceOf(Error);
        expect((failure as Error).message.split("\n")[0], `failed after ${JSON.stringify(trail)}`).toMatch(/rows of panel 5/);
        expect(mutation.applied(), "the mutation was applied to real queries").toBeGreaterThan(0);
        expect(trail, "everything before the Content ID step passed").toEqual(["client", "type", "global", "content-type"]);
        await mutation.restore();
    });

    // A second negative control, at the same step but for the other way a wrong pass can happen: the table is empty because the
    // queries FAILED, not because the selection matches nothing. Only the queries of the narrowed Content ID are answered with
    // HTTP 500, from the moment the control is used; the flow must fail at that step, not at an earlier one and not succeed.
    test("mutation: failed queries cannot stand in for an empty or narrowed Content ID selection", async ({ signedIn, ui }) => {
        const context = seeded(ui);
        const { plan } = filterPlan(context.state);
        expect(plan, "the run state supports the filter flow").toBeDefined();
        const trail: string[] = [];
        const failing = failContentIdQueries(signedIn, ui.grafanaUrl, plan?.contentTarget ?? "");
        const failure = await filteredFlow(signedIn, context, async () => undefined, trail, failing.start).then(
            () => undefined,
            (error: unknown) => error,
        );
        expect(failure, "the flow must fail while the narrowed Content ID queries fail").toBeInstanceOf(Error);
        expect(failing.failed(), "the controlled failure was applied to real queries").toBeGreaterThan(0);
        expect(trail, "everything before the Content ID step passed and the step itself did not").toEqual(["client", "type", "global", "content-type"]);
        const message = (failure as Error).message;
        if (plan?.contentBranch === "empty") {
            // the recorded failure ends the wait at once and its reason is in the output
            expect(message.split("\n")[0], `the step failed for the right reason after ${JSON.stringify(trail)}`).toMatch(/no datasource query for the narrowed Content ID .* failed/);
            expect(message, "the recorded reason is in the output").toContain("HTTP 500");
        } else {
            expect(message.split("\n")[0], `the step failed for the right reason after ${JSON.stringify(trail)}`).toMatch(/rows of panel 5/);
        }
        await failing.restore();
    });
});
