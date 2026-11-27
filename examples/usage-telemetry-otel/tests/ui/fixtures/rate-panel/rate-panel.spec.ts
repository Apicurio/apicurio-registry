import { expect, test, type Page } from "@playwright/test";

import { expectRatePanelSettled, noValueText, PANEL, panelBox } from "../../support/dashboard.js";

// What expectRatePanelSettled accepts, on static markup that models the hosted Grafana 13.3.1 snapshot of the rate panel: the legend
// is a table whose name cell holds `button "DESERIALIZE"` (and a sibling `button "Edit color for DESERIALIZE"`), and the series name
// is the button's ACCESSIBLE NAME. The markup is a model of that snapshot, not Grafana's own DOM (which the hosted artifacts do not
// contain): the legend button here has an aria-label and no text of its own, which is the case the text lookup of the first version
// could not find. The real DOM is proven only by a hosted run.
const NO_RATE = noValueText(PANEL.rate);
const OPERATIONS = ["DESERIALIZE", "SERIALIZE", "UNKNOWN"];

const panel = (id: number, inner: string): string => `<section data-testid="dashboard-panel-${id}" aria-label="panel ${id}">${inner}</section>`;
const legendRow = (name: string, named: "label" | "text"): string =>
    `<tr><td><button aria-label="Edit color for ${name}"><svg width="8" height="8"></svg></button></td>` +
    `<td>${named === "label" ? `<button aria-label="${name}"><svg width="8" height="8"></svg></button>` : `<button>${name}</button>`}</td><td>0.000</td><td>0.000</td></tr>`;
const legend = (names: string[], named: "label" | "text" = "label"): string => `<table><tbody>${names.map((name) => legendRow(name, named)).join("")}</tbody></table>`;

async function show(page: Page, markup: string): Promise<void> {
    await page.setContent(`<!doctype html><html><body>${markup}</body></html>`);
}
const refused = async (page: Page, operations: string[]): Promise<void> => {
    await expect(expectRatePanelSettled(page, operations)).rejects.toThrow(/toBeVisible/);
};

/** The first version of the check, kept to show what it did with the same markup. */
async function firstVersion(page: Page, operations: string[]): Promise<void> {
    const box = panelBox(page, PANEL.rate);
    const noRate = box.getByText(NO_RATE);
    const legendNames = box.getByText(new RegExp(`^(${operations.join("|")})$`));
    await expect(noRate.or(legendNames).first()).toBeVisible();
}

test("the no-rate text of the panel settles it, exact and visible", async ({ page }) => {
    await show(page, panel(PANEL.rate, `<div>${NO_RATE}</div>`));
    await expectRatePanelSettled(page, OPERATIONS);
});

test("a legend whose buttons have an accessible name and no text settles it (the hosted shape); the first version did not find it", async ({ page }) => {
    await show(page, panel(PANEL.rate, legend(OPERATIONS)));
    await expectRatePanelSettled(page, OPERATIONS);
    await expect(firstVersion(page, OPERATIONS), "the first version's text lookup cannot see a name that is not text").rejects.toThrow(/toBeVisible/);
});

test("a legend whose buttons carry the name as text settles it too", async ({ page }) => {
    await show(page, panel(PANEL.rate, legend(OPERATIONS, "text")));
    await expectRatePanelSettled(page, OPERATIONS);
});

test("a legend with only some of the expected operations settles it", async ({ page }) => {
    await show(page, panel(PANEL.rate, legend(["SERIALIZE"])));
    await expectRatePanelSettled(page, OPERATIONS);
    await expectRatePanelSettled(page, ["SERIALIZE"]);
});

test("a panel that is still loading is not settled", async ({ page }) => {
    await show(page, panel(PANEL.rate, `<div aria-label="data-testid Loading indicator" role="status"></div>`));
    await refused(page, OPERATIONS);
});

test("an unrelated button, or only the color buttons of a legend, is not settled", async ({ page }) => {
    await show(page, panel(PANEL.rate, `<button>Refresh</button><button aria-label="Panel status"></button>`));
    await refused(page, OPERATIONS);
    await show(page, panel(PANEL.rate, `<table><tbody><tr><td><button aria-label="Edit color for SERIALIZE"></button></td></tr></tbody></table>`));
    await refused(page, OPERATIONS);
});

test("an operation that the selection does not have is not accepted", async ({ page }) => {
    await show(page, panel(PANEL.rate, legend(["UNKNOWN"])));
    await refused(page, ["SERIALIZE"]);
    await refused(page, []);
    await show(page, panel(PANEL.rate, legend(["SERIALIZE_EXTRA", "serialize"])));
    await refused(page, ["SERIALIZE"]);
});

test("a legend or a no-rate text of another panel does not settle the rate panel", async ({ page }) => {
    await show(page, panel(PANEL.rate, `<div aria-label="data-testid Loading indicator"></div>`) + panel(PANEL.table, legend(OPERATIONS) + `<div>${NO_RATE}</div>`));
    await refused(page, OPERATIONS);
});

test("the no-rate text must be the whole text and visible", async ({ page }) => {
    await show(page, panel(PANEL.rate, `<div>${NO_RATE} (extra words)</div>`));
    await refused(page, OPERATIONS);
    await show(page, panel(PANEL.rate, `<div style="display: none">${NO_RATE}</div>`));
    await refused(page, OPERATIONS);
});
