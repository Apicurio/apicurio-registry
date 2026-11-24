import type { Route } from "@playwright/test";

import { changeSelection, chooseSingle, expectRows, expectUrlValues, openDashboard, PANEL, panelValue } from "../support/dashboard.js";
import { expect, test } from "../support/fixtures.js";
import { sortedRows, totalOf } from "../support/state.js";

// Input: the processes scenario (./test-ui.sh --scenario FILE --grafana-url URL), like process-switch.spec.ts.
//
// The client filter depends on the Registry process: choosing another process makes Grafana ask again for the client values of THAT
// process. This test holds that one real request (the one whose selector names the second process; the request of the initial load
// names the first and is not touched), waits until it is held and the client control is visibly loading, and only then starts the
// one-shot client selection. The answer is released unchanged after a read could have happened; nothing is mocked. The only
// instrumentation is on Locator.isChecked, which still reads the real control: a state read taken while the answer is held means the
// selection used a control that was still loading, and ends the test with that reason instead of letting a stale state decide.
// The fixed delay is the fault injected by this test, the same as in variable-readiness.spec.ts; the helper under test must not wait for
// it with a pause of its own.
const DELAY_MS = 3000;
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

    let phase: "idle" | "held" | "released" = "idle";
    let heldUrl = "";
    let release: () => void = () => undefined;
    const answer = new Promise<void>((resolve) => {
        release = resolve;
    });
    const secondProcessClients = (url: URL): boolean =>
        url.pathname.endsWith(CLIENT_VALUES) && url.searchParams.getAll("match[]").some((selector) => selector.includes(`service_instance_id="${scenario.other}"`));
    const hold = async (route: Route): Promise<void> => {
        if (phase !== "idle") {
            await route.continue();
            return;
        }
        phase = "held";
        heldUrl = decodeURIComponent(route.request().url());
        await Promise.race([answer, new Promise<void>((resolve) => setTimeout(resolve, DELAY_MS))]);
        phase = "released";
        await route.continue();
    };
    const locatorPrototype = Object.getPrototypeOf(signedIn.getByRole("option")) as { isChecked: (options?: unknown) => Promise<boolean> };
    const nativeIsChecked = locatorPrototype.isChecked;
    let heldReads = 0;
    locatorPrototype.isChecked = async function (this: unknown, options?: unknown): Promise<boolean> {
        if (phase === "held") {
            heldReads += 1;
            throw new Error("a filter checkbox was read while the client values of the second process were still being loaded (the selection used a control that was still loading)");
        }
        return nativeIsChecked.call(this, options);
    };
    await signedIn.route(secondProcessClients, hold);
    try {
        await chooseSingle(signedIn, "instance", scenario.other);
        await expect.poll(() => phase, { message: "the client values request of the second process was intercepted" }).toBe("held");
        expect(heldUrl, "the held request is the one of the second process").toContain(`service_instance_id="${scenario.other}"`);
        expect(heldUrl, "the held request is not the one of the first process").not.toContain(scenario.primary);
        // the control really is loading now, and nothing was chosen in it yet
        const clientField = signedIn.getByTestId(/^data-testid Dashboard template variables submenu Label Client ID/);
        await expect(clientField.locator('[aria-label="data-testid Loading indicator"]'), "the client control is loading while its answer is held").toBeVisible();
        expect(new URL(signedIn.url()).searchParams.getAll("var-client"), "no client is chosen yet").not.toEqual([client]);

        await changeSelection(signedIn, "client", { uncheck: ["All"], check: [client] });
        expect(phase, "the selection ended only after the answer was released").toBe("released");
        await expectUrlValues(signedIn, "var-instance", [scenario.other]);
        await expectUrlValues(signedIn, "var-client", [client]);
        await expectRows(signedIn, PANEL.table, sortedRows(chosen));
        await expect(panelValue(signedIn, PANEL.total)).toHaveText(String(totalOf(chosen)));
        expect(heldReads, "no state was read from the control while its answer was held").toBe(0);
    } finally {
        release();
        locatorPrototype.isChecked = nativeIsChecked;
        // the held answer is released first, then the interception is removed once its in-flight handler has finished
        await signedIn.unrouteAll({ behavior: "wait" });
    }
    console.log(`dependent variable readiness: ${heldReads} state read(s) were taken while the answer of the second process was held`);
});
