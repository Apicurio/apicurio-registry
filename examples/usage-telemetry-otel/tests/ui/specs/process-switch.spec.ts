import { chooseSingle, expectRows, openDashboard, PANEL, panelBox, panelValue } from "../support/dashboard.js";
import { expect, test } from "../support/fixtures.js";
import { sortedRows, totalOf } from "../support/state.js";

// Input: the processes scenario of the lifecycle-owned scenarios.json (./test-ui.sh --scenario FILE --grafana-url URL): two real
// Registry processes that reuse the same typed identifiers and the rows each of them must show.
test("two Registry processes that reuse identifiers each show their own rows", async ({ signedIn, ui }) => {
    const scenario = ui.scenario;
    if (scenario === undefined) {
        throw new Error("this test needs the processes scenario (UI_MODE=scenario)");
    }
    const reused = scenario.primaryRows.filter((row) =>
        scenario.otherRows.some(
            (other) => other.clientId === row.clientId && other.identifierType === row.identifierType && other.identifierValue === row.identifierValue && other.operation === row.operation,
        ),
    );
    expect(reused.length, "the scenario must reuse at least one typed identifier in both processes").toBeGreaterThan(0);

    await openDashboard(signedIn, { grafanaUrl: ui.grafanaUrl, datasourceUid: ui.datasourceUid, instance: scenario.primary, fromMs: scenario.fromMs, toMs: scenario.toMs });
    await expectRows(signedIn, PANEL.table, sortedRows(scenario.primaryRows));
    await expect(panelValue(signedIn, PANEL.total)).toHaveText(String(totalOf(scenario.primaryRows)));
    for (const process of [scenario.primary, scenario.other]) {
        await expect(panelBox(signedIn, PANEL.processes).getByRole("gridcell", { name: process, exact: true })).toHaveCount(1);
    }

    await chooseSingle(signedIn, "instance", scenario.other);
    await expectRows(signedIn, PANEL.table, sortedRows(scenario.otherRows));
    await expect(panelValue(signedIn, PANEL.total)).toHaveText(String(totalOf(scenario.otherRows)));

    await chooseSingle(signedIn, "instance", scenario.primary);
    await expectRows(signedIn, PANEL.table, sortedRows(scenario.primaryRows));
});
