import { changeSelection, expectUrlValues, openDashboard } from "../support/dashboard.js";
import { expect, test } from "../support/fixtures.js";
import { seeded, seededView } from "../support/flows.js";
import { createHold, watchReadinessStep } from "../lib/held-response.mjs";
import { filterPlan } from "../lib/plan.mjs";

// A controlled delay against the real Grafana, with the exact ordering that failed the public no-match test: the answer that fills the
// client variable's options is held back, the control is still loading (its options list shows only an unchecked "All" and it has no
// selected value) when a one-shot selection starts, and the answer arrives right after a checkbox state has been read. The Grafana
// request is held and then released unchanged; nothing is mocked.
// The hold is released by one thing: the selection reaching the helper's readiness step. The instrumentation on Locator.locator reports
// the lookup of the loading indicator, which the helper makes first for every variable it is about to use, and it is armed only right
// before the selection starts, so a slow setup or slow loading assertions never release anything. The deadline exists to fail: it opens
// the gate, and the verdict then refuses the run (a helper without a readiness step would otherwise pass by timeout). The finally block
// releases and drains the interception whatever happened. The other instrumentation is on Locator.isChecked, which still reads the real
// control: a read taken while the answer is held is a premature read; it releases the answer, waits until the real control has changed,
// and returns the snapshot it read before, so a helper that decides from the first state it sees acts on that stale read.
const DEADLINE_MS = 45_000;
const LOADING_INDICATOR = '[aria-label="data-testid Loading indicator"]';

test("a one-shot selection waits for the variable to finish loading: the label values answer is held until the selection reaches its readiness step", async ({ signedIn, ui }) => {
    const context = seeded(ui);
    const client = filterPlan(context.state).plan?.client ?? "";
    expect(client, "the run state supports the filter flow").not.toBe("");
    const hold = createHold({ deadlineMs: DEADLINE_MS, timers: { startTimer: (fire, ms) => setTimeout(fire, ms), stopTimer: (timer) => clearTimeout(timer as NodeJS.Timeout) } });
    await signedIn.route(/\/label\/apicurio_registry_client_id\/values/, async (route) => {
        await hold.held(() => route.continue());
    });
    const locatorPrototype = Object.getPrototypeOf(signedIn.getByRole("option")) as { isChecked: (options?: unknown) => Promise<boolean>; locator: (selector: string, ...rest: unknown[]) => unknown };
    const nativeIsChecked = locatorPrototype.isChecked;
    let staleReads = 0;
    locatorPrototype.isChecked = async function (this: unknown, options?: unknown): Promise<boolean> {
        const snapshot = await nativeIsChecked.call(this, options);
        if (hold.phase() === "held") {
            staleReads += 1;
            hold.release("premature-read");
            await expect.poll(() => nativeIsChecked.call(this, options), { message: "the real control changed once the answer arrived" }).not.toBe(snapshot);
        }
        return snapshot;
    };
    const stopWatching = watchReadinessStep(locatorPrototype, () => hold.signalReadiness(), LOADING_INDICATOR);
    try {
        await openDashboard(signedIn, seededView(context));
        await expect.poll(() => hold.phase(), { message: "the options request of the client variable was intercepted and is held while the selection starts" }).toBe("held");
        hold.arm();
        await changeSelection(signedIn, "client", { uncheck: ["All"], check: [client] });
        await expectUrlValues(signedIn, "var-client", [client]);
    } finally {
        stopWatching();
        locatorPrototype.isChecked = nativeIsChecked;
        hold.drain();
        await signedIn.unrouteAll({ behavior: "wait" });
    }
    expect(hold.verdict(), "the answer was held until the selection reached the readiness step").toEqual([]);
    expect(hold.phase(), "the options request of the client variable was really held and released").toBe("released");
    expect(staleReads, "no checkbox state was read from the client control while its answer was held").toBe(0);
    console.log(`variable readiness: ${staleReads} state read(s) were taken while the answer was still held`);
});
