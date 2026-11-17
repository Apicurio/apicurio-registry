import { describe, expect, it, vi } from "vitest";

// Mock the artifact types service module: the utils import it only for the ArtifactTypes
// constants, but loading the real module pulls the full service (and PatternFly CSS) into
// the test runner, which node cannot parse.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {
        MCP_TOOL: "MCP_TOOL"
    }
}));

import {
    compatibilityNotices,
    compatibilitySummary,
    compatibilityViewState,
    showCompatibleToolsTab,
    verdictLabel
} from "./McpToolCompatibility.utils";

describe("showCompatibleToolsTab", () => {
    it("shows the tab for an MCP tool version", () => {
        expect(showCompatibleToolsTab("MCP_TOOL", "ENABLED")).toBe(true);
        expect(showCompatibleToolsTab("MCP_TOOL", "DEPRECATED")).toBe(true);
        expect(showCompatibleToolsTab("MCP_TOOL", "DRAFT")).toBe(true);
    });

    it("hides the tab for other artifact types and for a disabled version", () => {
        expect(showCompatibleToolsTab("JSON", "ENABLED")).toBe(false);
        expect(showCompatibleToolsTab("AGENT_CARD", "ENABLED")).toBe(false);
        expect(showCompatibleToolsTab(undefined, undefined)).toBe(false);
        expect(showCompatibleToolsTab("MCP_TOOL", "DISABLED")).toBe(false);
    });
});

describe("compatibilityViewState", () => {
    it("shows loading first, then an error", () => {
        expect(compatibilityViewState(true, false, undefined)).toBe("loading");
        expect(compatibilityViewState(false, true, undefined)).toBe("error");
    });

    it("shows unavailable when the API has no compatibility information", () => {
        expect(compatibilityViewState(false, false, undefined)).toBe("unavailable");
    });

    it("shows why the tool was not compared when the response has top-level limitations", () => {
        const results = {
            evaluated: 0,
            results: [],
            limitations: [{ code: "SOURCE_HAS_NO_OUTPUT_SCHEMA", side: "PRODUCER" as const, pointer: "", message: "m" }]
        };
        expect(compatibilityViewState(false, false, results)).toBe("notComparable");
    });

    it("shows empty when no tool was evaluated, and the results otherwise", () => {
        expect(compatibilityViewState(false, false, { results: [], limitations: [] })).toBe("empty");
        expect(compatibilityViewState(false, false, { results: [{ verdict: "COMPATIBLE" }], limitations: [] }))
            .toBe("results");
    });
});

describe("verdictLabel", () => {
    it("gives each verdict its own text and colour", () => {
        expect(verdictLabel("COMPATIBLE")).toEqual({ text: "Compatible", color: "green" });
        expect(verdictLabel("INCOMPATIBLE")).toEqual({ text: "Incompatible", color: "red" });
        expect(verdictLabel("INDETERMINATE")).toEqual({ text: "Indeterminate", color: "orange" });
    });
});

describe("compatibilitySummary", () => {
    it("counts the verdicts from the totals", () => {
        expect(compatibilitySummary({ evaluated: 3, totals: { compatible: 1, incompatible: 1, indeterminate: 1 } }))
            .toBe("Compared with 3 other MCP tools: 1 compatible, 1 incompatible, 1 indeterminate.");
        expect(compatibilitySummary({ evaluated: 1, totals: { compatible: 1, incompatible: 0, indeterminate: 0 } }))
            .toBe("Compared with 1 other MCP tool: 1 compatible, 0 incompatible, 0 indeterminate.");
    });
});

describe("compatibilityNotices", () => {
    it("has no notice when every tool was compared and every result is shown", () => {
        expect(compatibilityNotices({ complete: true, scanLimit: 500, count: 1, results: [{}] })).toEqual([]);
    });

    it("notices a scan that stopped at the scan limit", () => {
        expect(compatibilityNotices({ complete: false, scanLimit: 500, count: 1, results: [{}] }))
            .toEqual(["Only the newest 500 MCP tools were compared."]);
    });

    it("notices results beyond the page shown", () => {
        expect(compatibilityNotices({ complete: true, scanLimit: 500, count: 3, results: [{}, {}] }))
            .toEqual(["Showing the first 2 of 3 results."]);
    });
});
