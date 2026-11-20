// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { ConsumerVersionHeatmap } from "@sdk/lib/generated-client/models";
import { ArtifactUsageTabContent } from "./ArtifactUsageTabContent";

const getConsumerVersionHeatmap = vi.fn();
vi.mock("@services/useUsageService.ts", () => ({
    useUsageService: () => ({ getConsumerVersionHeatmap })
}));

afterEach(() => {
    cleanup();
    vi.resetAllMocks();
});

describe("ArtifactUsageTabContent", () => {
    it.each([
        { versionsBehind: 0, driftAlert: false, text: "Current", color: "green" },
        { versionsBehind: 1, driftAlert: false, text: "1 behind", color: "orange" },
        { versionsBehind: 2, driftAlert: false, text: "2 behind", color: "orange" },
        { versionsBehind: 1, driftAlert: true, text: "1 behind", color: "red" },
        { versionsBehind: 2, driftAlert: true, text: "2 behind", color: "red" },
        { versionsBehind: 0, driftAlert: true, text: "Current", color: "green" }
    ])("shows $text in $color for versionsBehind=$versionsBehind and driftAlert=$driftAlert", async ({ versionsBehind, driftAlert, text, color }) => {
        const heatmap: ConsumerVersionHeatmap = {
            versions: ["1", "2", "3"],
            consumers: [{
                clientId: "fraud-detector",
                versions: { additionalData: { [String(3 - versionsBehind)]: 3 } },
                versionsBehind,
                driftAlert
            }]
        };
        getConsumerVersionHeatmap.mockResolvedValue(heatmap);

        render(<ArtifactUsageTabContent artifact={{ groupId: "demo", artifactId: "transactions" }} />);

        const row = await screen.findByRole("row", { name: /fraud-detector/ });
        const driftCell = within(row).getByRole("cell", { name: text });
        expect(driftCell).toHaveTextContent(text);
        expect(within(driftCell).getByText(text).closest(".pf-v6-c-label")).toHaveClass(`pf-m-${color}`);
    });
});
