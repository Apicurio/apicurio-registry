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

    it("renders list of compatible tools when non-empty results are returned", async () => {
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
    });
});
