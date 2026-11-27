import { test } from "../support/fixtures.js";
import { filteredFlow, overviewFlow, seeded } from "../support/flows.js";
import { writeShot } from "../support/shots.js";

// The same flows and assertions as usage.spec.ts; the difference is where the pictures go: PNG files of this invocation.
test.describe("screenshots of the seeded run", () => {
    test("overview", async ({ signedIn, ui }, testInfo) => {
        await overviewFlow(signedIn, seeded(ui), writeShot(signedIn, testInfo, ui.runDir));
    });

    test("filtered", async ({ signedIn, ui }, testInfo) => {
        await filteredFlow(signedIn, seeded(ui), writeShot(signedIn, testInfo, ui.runDir));
    });
});
