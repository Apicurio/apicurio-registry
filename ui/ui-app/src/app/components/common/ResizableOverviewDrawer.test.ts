import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const directory = dirname(fileURLToPath(import.meta.url));
const componentSource = readFileSync(resolve(directory, "ResizableOverviewDrawer.tsx"), "utf8");
const stylesheet = readFileSync(resolve(directory, "ResizableOverviewDrawer.css"), "utf8");
const consumerPaths = [
    "../../pages/artifact/components/tabs/ArtifactOverviewTabContent.tsx",
    "../../pages/group/components/tabs/GroupOverviewTabContent.tsx",
    "../../pages/version/components/tabs/VersionOverviewTabContent.tsx"
];

describe("ResizableOverviewDrawer responsive layout", () => {
    it("uses fluid desktop sizing instead of fixed drawer constraints", () => {
        expect(componentSource).toContain("defaultSize={DEFAULT_PANE_WIDTH}");
        expect(componentSource).toContain("const DEFAULT_PANE_WIDTH = \"40%\"");
        expect(componentSource).toContain("const MINIMUM_PANE_WIDTH = \"300px\"");
        expect(componentSource).not.toContain("defaultSize=\"500px\"");
        expect(componentSource).not.toContain("minSize=\"400px\"");
    });

    it("stacks panes and keeps table overflow inside the drawer at narrow widths", () => {
        expect(stylesheet).toContain("@media (max-width: 1024px)");
        expect(stylesheet).toContain("flex-direction: column");
        expect(stylesheet).toContain("width: 100% !important");
        expect(stylesheet).toContain("overflow-x: auto");
        expect(stylesheet).toContain("width: max-content");
        expect(stylesheet).toContain("min-width: max-content");
    });

    it("is used by all overview drawer consumers", () => {
        for (const consumerPath of consumerPaths) {
            const consumerSource = readFileSync(resolve(directory, consumerPath), "utf8");
            expect(consumerSource).toContain("ResizableOverviewDrawer");
        }
    });
});
