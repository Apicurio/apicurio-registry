import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

import type { Page, TestInfo } from "@playwright/test";

import type { Shot } from "./flows.js";

/** The picture shows the dashboard, not the pointer or the keyboard focus: the mouse leaves the panels and nothing keeps focus. */
const capture = async (page: Page): Promise<Buffer> => {
    await page.mouse.move(0, 0);
    await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
    return page.screenshot({ animations: "disabled", caret: "hide" });
};

/** The screenshot becomes an attachment of the test, so it is in the HTML report of the run. */
export const attachShot =
    (page: Page, testInfo: TestInfo): Shot =>
    async (name) => {
        await testInfo.attach(name, { body: await capture(page), contentType: "image/png" });
    };

/** The screenshot is written to the screenshots directory of this invocation and attached to the report as well. */
export const writeShot =
    (page: Page, testInfo: TestInfo, runDir: string): Shot =>
    async (name) => {
        const directory = join(runDir, "screenshots");
        mkdirSync(directory, { recursive: true });
        const body = await capture(page);
        const path = join(directory, `${name}.png`);
        await testInfo.attach(name, { body, contentType: "image/png" });
        writeFileSync(path, body, { flag: "wx" });
    };
