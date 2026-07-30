import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { CompatibleMcpToolsViewer } from "./CompatibleMcpToolsViewer";
import { McpToolSearchResults } from "../../../services/useMcpToolsService";

const mockGetCompatibleMcpTools = vi.fn();
vi.mock("../../../services/useMcpToolsService", () => ({
    useMcpToolsService: () => ({
        getCompatibleMcpTools: mockGetCompatibleMcpTools
    })
}));

describe("CompatibleMcpToolsViewer", () => {
    it("renders loading state initially and empty state when 0 compatible tools are returned", async () => {
        const emptyResult: McpToolSearchResults = {
            count: 0,
            tools: []
        };
        mockGetCompatibleMcpTools.mockResolvedValueOnce(emptyResult);

        render(<CompatibleMcpToolsViewer groupId="test-group" artifactId="test-tool" />);

        expect(screen.getByTestId("loading-state")).toBeInTheDocument();

        await waitFor(() => {
            expect(screen.getByTestId("empty-state")).toBeInTheDocument();
        });

        expect(screen.getByText("No Compatible Tools Found")).toBeInTheDocument();
        expect(mockGetCompatibleMcpTools).toHaveBeenCalledWith("test-group", "test-tool", undefined);
    });

    it("renders list of compatible tools and badge with totalCount, omitting truncation indicator when totalCount === tools.length", async () => {
        const nonEmptyResult: McpToolSearchResults = {
            count: 1,
            tools: [
                {
                    groupId: "test-group",
                    artifactId: "compat-tool-1",
                    name: "result_formatter",
                    title: "Result Formatter Tool",
                    description: "Formats output results into text",
                    parameters: ["result"]
                }
            ]
        };
        mockGetCompatibleMcpTools.mockResolvedValueOnce(nonEmptyResult);

        render(<CompatibleMcpToolsViewer groupId="test-group" artifactId="test-tool" />);

        await waitFor(() => {
            expect(screen.getByTestId("tools-list")).toBeInTheDocument();
        });

        expect(screen.getByText("Result Formatter Tool")).toBeInTheDocument();
        expect(screen.getByText("test-group / compat-tool-1")).toBeInTheDocument();
        expect(screen.getByText("Formats output results into text")).toBeInTheDocument();
        expect(screen.getByText("result")).toBeInTheDocument();

        // Scenario (1): Badge displays totalCount (1)
        expect(screen.getByText("1")).toBeInTheDocument();

        // Scenario (3): Truncation indicator does NOT render when totalCount === tools.length
        expect(screen.queryByTestId("truncation-indicator")).not.toBeInTheDocument();
        expect(screen.queryByText(/Showing .* of .* compatible tools/)).not.toBeInTheDocument();
    });

    it("displays badge with totalCount and renders truncation indicator when totalCount > tools.length", async () => {
        const truncatedResult: McpToolSearchResults = {
            count: 5,
            tools: [
                {
                    groupId: "test-group",
                    artifactId: "compat-tool-1",
                    name: "result_formatter",
                    title: "Result Formatter Tool",
                    description: "Formats output results into text",
                    parameters: ["result"]
                }
            ]
        };
        mockGetCompatibleMcpTools.mockResolvedValueOnce(truncatedResult);

        render(<CompatibleMcpToolsViewer groupId="test-group" artifactId="test-tool" />);

        await waitFor(() => {
            expect(screen.getByTestId("tools-list")).toBeInTheDocument();
        });

        // Scenario (1): Badge displays totalCount (5), NOT tools.length (1)
        expect(screen.getByText("5")).toBeInTheDocument();
        expect(screen.queryByText("1")).not.toBeInTheDocument();

        // Scenario (2): Truncation message "Showing 1 of 5 compatible tools" renders when totalCount > tools.length
        expect(screen.getByTestId("truncation-indicator")).toBeInTheDocument();
        expect(screen.getByText("Showing 1 of 5 compatible tools")).toBeInTheDocument();
    });
});
