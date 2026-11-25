import type { Route } from "@playwright/test";

import { changeSelection, chooseSingle, expectRows, expectUrlValues, openDashboard, PANEL, panelValue } from "../support/dashboard.js";
import { expect, test } from "../support/fixtures.js";
import { createHold, watchReadinessStep } from "../lib/held-response.mjs";
import { sortedRows, totalOf } from "../support/state.js";

// Input: the processes scenario (./test-ui.sh --scenario FILE --grafana-url URL), like process-switch.spec.ts.
//
// The client filter depends on the Registry process: choosing another process makes Grafana ask again for the client values of THAT
// process. This test holds that one real request (the one whose selector names the second process; the request of the initial load
// names the first and is not touched), waits until it is held and the client control is visibly loading, and only then starts the
// one-shot client selection. The answer is released unchanged, and only when the selection reaches the helper's readiness step (the
// lookup of the loading indicator, reported by an instrumentation on Locator.locator that is armed right before the selection starts, so
// the loading assertions of this test never release it); nothing is mocked. The other instrumentation is on Locator.isChecked, which
// still reads the real control: a state read taken while the answer is held means the selection used a control that was still
// loading, and ends the test with that reason instead of letting a stale state decide. The deadline exists to fail: it opens the gate
// and the verdict refuses the run, so a helper without a readiness step cannot pass by timeout; the finally block releases and drains
// the interception whatever happened. The helper under test must not wait with a pause of its own.
const DEADLINE_MS = 45_000;
const LOADING_INDICATOR = '[aria-label="data-testid Loading indicator"]';
const CLIENT_VALUES = "/label/apicurio_registry_client_id/values";

test("a client filter chosen right after a process change waits for the dependent client reload of that process", async ({ signedIn, ui }) => {
    const scenario = ui.scenario;
    if (scenario === undefined) {
        throw new Error("this test needs the processes scenario (UI_MODE=scenario)");
    }
    const clients = [...new Set(scenario.otherRows.map((row) => row.clientId))].sort();
    const client = clients.find((candidate) => scenario.otherRows.some((row) => row.clientId !== candidate));
    if (client === undefined) {
        throw new Error("the second process needs at least two clients, so that narrowing to one is a visible change");
    }
    const chosen = scenario.otherRows.filter((row) => row.clientId === client);

    await openDashboard(signedIn, { grafanaUrl: ui.grafanaUrl, datasourceUid: ui.datasourceUid, instance: scenario.primary, fromMs: scenario.fromMs, toMs: scenario.toMs });
    await expectRows(signedIn, PANEL.table, sortedRows(scenario.primaryRows));

    const hold = createHold({ deadlineMs: DEADLINE_MS, timers: { startTimer: (fire, ms) => setTimeout(fire, ms), stopTimer: (timer) => clearTimeout(timer as NodeJS.Timeout) } });
    let heldUrl = "";
    const secondProcessClients = (url: URL): boolean =>
        url.pathname.endsWith(CLIENT_VALUES) && url.searchParams.getAll("match[]").some((selector) => selector.includes(`service_instance_id="${scenario.other}"`));
    const holdRoute = async (route: Route): Promise<void> => {
        if (hold.phase() === "released") {
            await route.continue();
            return;
        }
        if (heldUrl === "") {
            heldUrl = decodeURIComponent(route.request().url());
        }
        await hold.held(() => route.continue());
    };
    const locatorPrototype = Object.getPrototypeOf(signedIn.getByRole("option")) as { isChecked: (options?: unknown) => Promise<boolean>; locator: (selector: string, ...rest: unknown[]) => unknown };
    const nativeIsChecked = locatorPrototype.isChecked;
    let heldReads = 0;
    locatorPrototype.isChecked = async function (this: unknown, options?: unknown): Promise<boolean> {
        if (hold.phase() === "held") {
            heldReads += 1;
            hold.release("premature-read");
            throw new Error("a filter checkbox was read while the client values of the second process were still being loaded (the selection used a control that was still loading)");
        }
        return nativeIsChecked.call(this, options);
    };
    const stopWatching = watchReadinessStep(locatorPrototype, () => hold.signalReadiness(), LOADING_INDICATOR);
    await signedIn.route(secondProcessClients, holdRoute);
    try {
        await chooseSingle(signedIn, "instance", scenario.other);
        await expect.poll(() => hold.phase(), { message: "the client values request of the second process was intercepted" }).toBe("held");
        expect(heldUrl, "the held request is the one of the second process").toContain(`service_instance_id="${scenario.other}"`);
        expect(heldUrl, "the held request is not the one of the first process").not.toContain(scenario.primary);
        // the control really is loading now, and nothing was chosen in it yet
        const clientField = signedIn.getByTestId(/^data-testid Dashboard template variables submenu Label Client ID/);
        await expect(clientField.locator(LOADING_INDICATOR), "the client control is loading while its answer is held").toBeVisible();
        expect(new URL(signedIn.url()).searchParams.getAll("var-client"), "no client is chosen yet").not.toEqual([client]);

        hold.arm();
        await changeSelection(signedIn, "client", { uncheck: ["All"], check: [client] });
        expect(hold.phase(), "the selection ended only after the answer was released").toBe("released");
        await expectUrlValues(signedIn, "var-instance", [scenario.other]);
        await expectUrlValues(signedIn, "var-client", [client]);
        await expectRows(signedIn, PANEL.table, sortedRows(chosen));
        await expect(panelValue(signedIn, PANEL.total)).toHaveText(String(totalOf(chosen)));
        expect(heldReads, "no state was read from the control while its answer was held").toBe(0);
        expect(hold.verdict(), "the answer was held until the selection reached the readiness step").toEqual([]);
    } finally {
        stopWatching();
        locatorPrototype.isChecked = nativeIsChecked;
        hold.drain();
        // the held answer is released first, then the interception is removed once its in-flight handler has finished
        await signedIn.unrouteAll({ behavior: "wait" });
    }
    console.log(`dependent variable readiness: ${heldReads} state read(s) were taken while the answer of the second process was held`);
});
