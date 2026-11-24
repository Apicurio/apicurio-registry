import { readFileSync } from "node:fs";

import { expect, type Locator, type Page, type Route } from "@playwright/test";

import { matchesDatasourceQuery } from "../lib/grafana.mjs";

interface DashboardJson {
    uid: string;
    title: string;
    panels: { id: number; type: string; title: string; fieldConfig?: { defaults?: { noValue?: string } } }[];
    templating: { list: { name: string; label?: string }[] };
}

/** The canonical dashboard of this repository: titles, ids and no-data texts are read from it, never copied. */
const dashboard = JSON.parse(
    readFileSync(new URL("../../../../../distro/metrics/usage/grafana/schema-usage.json", import.meta.url), "utf8"),
) as DashboardJson;

export const DASHBOARD_UID = dashboard.uid;

/** The panels by the role they play; assertDashboardShape checks the canonical file still has them at these ids. */
export const PANEL = { total: 1, overflow: 2, exportAge: 3, rate: 4, table: 5, processes: 6, help: 7 } as const;
const EXPECTED_TYPES: Record<number, string> = { 1: "stat", 2: "stat", 3: "stat", 4: "timeseries", 5: "table", 6: "table", 7: "text" };

export function assertDashboardShape(): void {
    for (const [id, type] of Object.entries(EXPECTED_TYPES)) {
        const panel = dashboard.panels.find((candidate) => candidate.id === Number(id));
        if (panel === undefined || panel.type !== type) {
            throw new Error(`the canonical dashboard has no ${type} panel with id ${id}; these tests need updating`);
        }
    }
}

export function panelTitle(id: number): string {
    const panel = dashboard.panels.find((candidate) => candidate.id === id);
    if (panel === undefined) {
        throw new Error(`no panel ${id}`);
    }
    return panel.title;
}

/** The text a panel shows for an empty or failed result, as the dashboard defines it. */
export function noValueText(id: number): string {
    const text = dashboard.panels.find((candidate) => candidate.id === id)?.fieldConfig?.defaults?.noValue;
    if (text === undefined) {
        throw new Error(`panel ${id} has no no-value text`);
    }
    return text;
}

export const DATA_PANEL_IDS = [PANEL.total, PANEL.overflow, PANEL.exportAge, PANEL.rate, PANEL.table, PANEL.processes];

export type Variable = "instance" | "client" | "type" | "operation" | "global_id" | "content_id";

function variableLabel(name: Variable): string {
    const label = dashboard.templating.list.find((variable) => variable.name === name)?.label;
    if (label === undefined) {
        throw new Error(`the canonical dashboard has no labelled variable ${name}`);
    }
    return label;
}

const escapeRegExp = (text: string): string => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** "2026-11-20 16:12:22", the way Grafana prints a range bound in UTC; fractions of a second are not shown. */
export const formatUtc = (ms: number): string => new Date(ms).toISOString().slice(0, 19).replace("T", " ");

export interface View {
    grafanaUrl: string;
    datasourceUid: string;
    instance: string;
    fromMs: number;
    toMs: number;
    clients?: string[];
    types?: string[];
    globalIds?: string[];
    contentIds?: string[];
}

export function dashboardUrl(view: View): string {
    const params = new URLSearchParams({
        orgId: "1",
        "var-datasource": view.datasourceUid,
        "var-instance": view.instance,
        from: String(view.fromMs),
        to: String(view.toMs),
    });
    const lists: [string, string[] | undefined][] = [
        ["var-client", view.clients],
        ["var-type", view.types],
        ["var-global_id", view.globalIds],
        ["var-content_id", view.contentIds],
    ];
    for (const [name, values] of lists) {
        for (const value of values ?? []) {
            params.append(name, value);
        }
    }
    return `${view.grafanaUrl}/d/${DASHBOARD_UID}?${params.toString()}`;
}

export const panelBox = (page: Page, id: number): Locator => page.locator(`[data-testid$="-panel-${id}"]`);
export const panelValue = (page: Page, id: number): Locator => panelBox(page, id).getByTestId("data-testid panel content");
export const errorMarkers = (page: Page): Locator => page.getByTestId("data-testid Panel status error");

/** The range selector's name carries the bounds Grafana is actually showing. */
export const rangeButton = (page: Page): Locator => page.getByRole("button", { name: /^Time range selected/ });

/** Opens the dashboard at an exact view and waits until Grafana shows that range and every panel title. */
export async function openDashboard(page: Page, view: View): Promise<void> {
    await page.goto(dashboardUrl(view));
    await expect(rangeButton(page)).toHaveAccessibleName(`Time range selected: ${formatUtc(view.fromMs)} to ${formatUtc(view.toMs)}`);
    for (const panel of dashboard.panels) {
        await expect(panelBox(page, panel.id).getByTestId(`data-testid Panel header ${panel.title}`)).toBeVisible();
    }
}

/** The rows of a table panel as "cell TAB cell ...", sorted; a row without data cells (the header) is not a row. */
export const readRows = (page: Page, id: number): Promise<string[]> =>
    panelBox(page, id)
        .getByRole("row")
        .evaluateAll((rows) =>
            rows
                .map((row) =>
                    Array.from(row.querySelectorAll('[role="gridcell"]'))
                        .map((cell) => (cell.textContent ?? "").trim())
                        .join("\t"),
                )
                .filter((text) => text !== "")
                .sort(),
        );

/** Polls the table until it shows exactly these rows: the expectation differs from stale data, so a pass means a refresh. */
export async function expectRows(page: Page, id: number, expected: string[]): Promise<void> {
    await expect.poll(() => readRows(page, id), { message: `rows of panel ${id}` }).toEqual([...expected].sort());
}

export async function expectUrlValues(page: Page, name: string, values: string[]): Promise<void> {
    await expect.poll(() => new URL(page.url()).searchParams.getAll(name), { message: `URL parameter ${name}` }).toEqual(values);
}

/**
 * Waits until a variable's control has really loaded, before anything is read from it or clicked in it. While Grafana is still
 * loading the options of a variable, its control shows a loading indicator inside its label, has no selected value (the control's
 * own test id ends in "value link text -input", with no value in it), and lists only an unchecked "All", so a checkbox state read
 * then is not the state the variable will have. Before a control is opened both signs are required: the indicator is gone, and the
 * control carries a selected value (a loading indicator that has not appeared yet is not evidence). Inside the open control only the
 * indicator is checked, because unchecking "All" legitimately leaves no selected value until the selection is committed. A reload of a
 * variable that depends on another one makes the signs appear again, which is why this is checked before every decision.
 */
async function expectVariableReady(page: Page, variable: Variable, selectedValue: boolean): Promise<void> {
    const label = variableLabel(variable);
    const field = page.getByTestId(`data-testid Dashboard template variables submenu Label ${label}`);
    // without the label the loading indicator can never be found, and "no indicator" would pass for a markup that changed
    await expect(field, `the label of the ${variable} variable is on the dashboard`).toHaveCount(1);
    await expect(field.locator('[aria-label="data-testid Loading indicator"]'), `the ${variable} variable has finished loading`).toHaveCount(0);
    if (selectedValue) {
        const combobox = page.getByRole("combobox", { name: new RegExp(`^${escapeRegExp(label)}`) });
        await expect(combobox, `the ${variable} variable has a selected value`).toHaveAttribute("data-testid", /value link text .+-input$/);
    }
}

async function toggle(page: Page, variable: Variable, combobox: Locator, value: string, wanted: boolean): Promise<void> {
    // the options list is virtualized and long: typing narrows it to the option that is wanted
    await combobox.fill(value);
    const option = page.getByRole("option", { name: value, exact: true });
    await expect(option).toBeVisible();
    await expectVariableReady(page, variable, false);
    const box = option.getByRole("checkbox");
    if ((await box.isChecked()) !== wanted) {
        await option.click();
    }
    if (wanted) {
        await expect(box).toBeChecked();
    } else {
        await expect(box).not.toBeChecked();
    }
}

/**
 * Changes a multi-select with the real controls. "All" is a checked option like any other, so choosing a specific value
 * explicitly unchecks All first; leaving it checked would keep the filter on All. Tabbing out commits the selection.
 */
export async function changeSelection(page: Page, variable: Variable, change: { uncheck?: string[]; check?: string[] }): Promise<void> {
    const combobox = page.getByRole("combobox", { name: new RegExp(`^${escapeRegExp(variableLabel(variable))}`) });
    await expectVariableReady(page, variable, true);
    await combobox.click();
    for (const value of change.uncheck ?? []) {
        await toggle(page, variable, combobox, value, false);
    }
    for (const value of change.check ?? []) {
        await toggle(page, variable, combobox, value, true);
    }
    await page.keyboard.press("Tab");
}

/** Chooses one value of a single-select such as the Registry process. */
export async function chooseSingle(page: Page, variable: Variable, value: string): Promise<void> {
    const combobox = page.getByRole("combobox", { name: new RegExp(`^${escapeRegExp(variableLabel(variable))}`) });
    await expectVariableReady(page, variable, true);
    await combobox.click();
    const option = page.getByRole("option", { name: value, exact: true });
    await expect(option).toBeVisible();
    await option.click();
    await expectUrlValues(page, `var-${variable}`, [value]);
}

/**
 * A controlled failure inside the browser: every datasource query of this Grafana answers HTTP 500. Nothing else is touched (not
 * the login, not the dashboard load), and the returned function removes the route again. This tests how the dashboard presents a
 * failed query; it does not claim that a real backend was down.
 */
export async function failDatasourceQueries(page: Page, grafanaUrl: string): Promise<() => Promise<void>> {
    const matches = (url: URL): boolean => matchesDatasourceQuery(grafanaUrl, url);
    const answer = (route: Route): Promise<void> =>
        route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "controlled browser-side failure" }) });
    await page.route(matches, answer);
    return () => page.unroute(matches, answer);
}
