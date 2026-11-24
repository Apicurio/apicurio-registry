import { changeSelection, expectUrlValues, openDashboard } from "../support/dashboard.js";
import { expect, test } from "../support/fixtures.js";
import { seeded, seededView } from "../support/flows.js";
import { filterPlan } from "../lib/plan.mjs";

// A controlled delay against the real Grafana, with the exact ordering that failed the public no-match test: the answer that fills the
// client variable's options is held back, the control is still loading (its options list shows only an unchecked "All" and it has no
// selected value) when a one-shot selection starts, and the answer arrives right after a checkbox state has been read. The Grafana
// request is held and then released unchanged; nothing is mocked. The only instrumentation is on Locator.isChecked, which still reads the
// real control: while the answer is still held it lets that real read happen, releases the answer, waits until the real control has
// changed, and then returns the snapshot it read before. A helper that decides from the first state it sees acts on that stale read.
// A helper that waits for the variable to be ready never reads while the answer is held, so the instrumentation does not apply to it.
// The fixed delay is the fault injected by this test (it also releases the answer for a helper that is waiting); the helper under test
// must not wait for it with a pause of its own.
const DELAY_MS = 3000;

test("a one-shot selection waits for the variable to finish loading: the label values answer is held, then released after a read", async ({ signedIn, ui }) => {
    const context = seeded(ui);
    const client = filterPlan(context.state).plan?.client ?? "";
    expect(client, "the run state supports the filter flow").not.toBe("");
    let phase: "idle" | "held" | "released" = "idle";
    let release: () => void = () => undefined;
    const answer = new Promise<void>((resolve) => {
        release = resolve;
    });
    await signedIn.route(/\/label\/apicurio_registry_client_id\/values/, async (route) => {
        phase = "held";
        await Promise.race([answer, new Promise<void>((resolve) => setTimeout(resolve, DELAY_MS))]);
        phase = "released";
        await route.continue();
    });
    const locatorPrototype = Object.getPrototypeOf(signedIn.getByRole("option")) as { isChecked: (options?: unknown) => Promise<boolean> };
    const nativeIsChecked = locatorPrototype.isChecked;
    let staleReads = 0;
    locatorPrototype.isChecked = async function (this: unknown, options?: unknown): Promise<boolean> {
        const snapshot = await nativeIsChecked.call(this, options);
        if (phase === "held") {
            staleReads += 1;
            release();
            await expect.poll(() => nativeIsChecked.call(this, options), { message: "the real control changed once the answer arrived" }).not.toBe(snapshot);
        }
        return snapshot;
    };
    try {
        await openDashboard(signedIn, seededView(context));
        await changeSelection(signedIn, "client", { uncheck: ["All"], check: [client] });
        await expectUrlValues(signedIn, "var-client", [client]);
    } finally {
        locatorPrototype.isChecked = nativeIsChecked;
    }
    expect(phase, "the options request of the client variable was really held and released").toBe("released");
    expect(staleReads, "no checkbox state was read from the client control while its answer was held").toBe(0);
    console.log(`variable readiness: ${staleReads} state read(s) were taken while the answer was still held`);
});
