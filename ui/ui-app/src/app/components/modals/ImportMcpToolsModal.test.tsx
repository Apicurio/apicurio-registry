// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ImportMcpToolsModal } from "./ImportMcpToolsModal";
import { McpToolImportRequest, McpToolImportResult } from "@sdk/lib/generated-client/models";

// The violation report imports these; the real ones need the browser's registry config global
// and mount Monaco, as InvalidContentModal.test.tsx explains.
vi.mock("@services/useArtifactTypesService.ts", () => ({
    ArtifactTypes: {}
}));
vi.mock("@app/components/codeEditor/RegistryEditors.tsx", () => ({
    RegistryCodeEditor: () => <pre />
}));
vi.mock("@app/components/common/DiffView.tsx", () => ({
    DiffView: () => <pre />
}));

const SECRET = "Bearer s3cr3t-value";

const result = (overrides: Partial<McpToolImportResult> = {}): McpToolImportResult => ({
    groupId: "servers",
    artifactId: "weather",
    status: "NEW_VERSION",
    version: "2",
    tools: [
        { name: "getWeather", groupId: "servers", artifactId: "weather.getWeather", version: "1", status: "CREATED" },
        {
            name: "getForecast", groupId: "servers", artifactId: "weather.getForecast", status: "REJECTED",
            causes: [{ description: "The declared 'type' of the schema was changed.", context: "/inputSchema/properties/days/type" }]
        }
    ],
    ...overrides
});

const onClose = vi.fn();

const modal = (onImport: (data: McpToolImportRequest) => Promise<McpToolImportResult>, isOpen = true) => (
    <ImportMcpToolsModal isOpen={isOpen} groupId="servers" artifactId="weather" onClose={onClose} onImport={onImport} />
);

const type = (testId: string, value: string): void => {
    fireEvent.change(screen.getByTestId(testId), { target: { value } });
};

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ImportMcpToolsModal", () => {
    it("needs a URL, and a name and value for every header, before it can run", () => {
        render(modal(vi.fn()));
        expect(screen.getByTestId("modal-btn-import")).toBeDisabled();
        expect(screen.getByTestId("modal-btn-preview")).toBeDisabled();

        type("form-mcp-url", "https://mcp.example.com/mcp");
        expect(screen.getByTestId("modal-btn-import")).toBeEnabled();

        fireEvent.click(screen.getByTestId("form-mcp-header-add"));
        type("form-mcp-header-name-0", "Authorization");
        expect(screen.getByTestId("modal-btn-import")).toBeDisabled();
        type("form-mcp-header-value-0", SECRET);
        expect(screen.getByTestId("modal-btn-import")).toBeEnabled();
    });

    it("previews with dryRun and sends the tool group and headers", async () => {
        const onImport = vi.fn().mockResolvedValue(result({ version: undefined }));
        render(modal(onImport));
        type("form-mcp-url", " https://mcp.example.com/mcp ");
        type("form-mcp-tool-group", "tools");
        fireEvent.click(screen.getByTestId("form-mcp-header-add"));
        type("form-mcp-header-name-0", "Authorization");
        type("form-mcp-header-value-0", SECRET);

        fireEvent.click(screen.getByTestId("modal-btn-preview"));

        await waitFor(() => expect(screen.getByTestId("mcp-import-result")).toBeInTheDocument());
        expect(onImport).toHaveBeenCalledWith({
            url: "https://mcp.example.com/mcp",
            dryRun: true,
            toolGroupId: "tools",
            headers: { additionalData: { Authorization: SECRET } }
        });
        expect(screen.getByTestId("mcp-import-summary")).toHaveTextContent("Nothing was written.");
    });

    it("keeps header values in a password field, never in the page text", async () => {
        render(modal(vi.fn().mockResolvedValue(result())));
        type("form-mcp-url", "https://mcp.example.com/mcp");
        fireEvent.click(screen.getByTestId("form-mcp-header-add"));
        type("form-mcp-header-name-0", "Authorization");
        type("form-mcp-header-value-0", SECRET);

        expect(screen.getByTestId("form-mcp-header-value-0")).toHaveAttribute("type", "password");
        fireEvent.click(screen.getByTestId("modal-btn-import"));
        await waitFor(() => expect(screen.getByTestId("mcp-import-result")).toBeInTheDocument());
        expect(document.body.textContent).not.toContain(SECRET);
    });

    it("shows each tool's status, and the violations of a rejected tool", async () => {
        render(modal(vi.fn().mockResolvedValue(result())));
        type("form-mcp-url", "https://mcp.example.com/mcp");

        fireEvent.click(screen.getByTestId("modal-btn-import"));

        await waitFor(() => expect(screen.getByTestId("mcp-import-result")).toBeInTheDocument());
        expect(screen.getByTestId("mcp-import-tool-getWeather")).toHaveTextContent("CREATED");
        expect(screen.getByTestId("mcp-import-tool-getForecast")).toHaveTextContent("REJECTED");
        expect(screen.getByTestId("mcp-import-rejected-getForecast"))
            .toHaveTextContent("The declared 'type' of the schema was changed.");
        expect(screen.getByTestId("mcp-import-summary")).toHaveTextContent("now at version 2.");
    });

    it("shows the server's error message", async () => {
        render(modal(vi.fn().mockRejectedValue({ status: 400, title: "The MCP server URL is not allowed by this registry." })));
        type("form-mcp-url", "https://other.example.com/mcp");

        fireEvent.click(screen.getByTestId("modal-btn-import"));

        await waitFor(() => expect(screen.getByTestId("mcp-import-error"))
            .toHaveTextContent("The MCP server URL is not allowed by this registry."));
        expect(screen.queryByTestId("mcp-import-result")).not.toBeInTheDocument();
    });

    it("reports on close whether an import wrote anything, so the page can reload", async () => {
        render(modal(vi.fn().mockResolvedValue(result())));
        type("form-mcp-url", "https://mcp.example.com/mcp");
        fireEvent.click(screen.getByTestId("modal-btn-preview"));
        await waitFor(() => expect(screen.getByTestId("mcp-import-result")).toBeInTheDocument());
        fireEvent.click(screen.getByTestId("modal-btn-close"));
        expect(onClose).toHaveBeenLastCalledWith(false);

        fireEvent.click(screen.getByTestId("modal-btn-import"));
        await waitFor(() => expect(screen.getByTestId("mcp-import-summary")).toHaveTextContent("now at version 2."));
        fireEvent.click(screen.getByTestId("modal-btn-close"));
        expect(onClose).toHaveBeenLastCalledWith(true);
    });

    it("cannot be closed while an import is running, so the reload after it is not lost", async () => {
        let finish: (r: McpToolImportResult) => void = () => undefined;
        render(modal(vi.fn().mockReturnValue(new Promise<McpToolImportResult>(resolve => { finish = resolve; }))));
        type("form-mcp-url", "https://mcp.example.com/mcp");
        fireEvent.click(screen.getByTestId("modal-btn-import"));

        expect(screen.getByTestId("modal-btn-close")).toBeDisabled();
        fireEvent.keyDown(document.body, { key: "Escape" });
        expect(onClose).not.toHaveBeenCalled();

        finish(result());
        await waitFor(() => expect(screen.getByTestId("mcp-import-summary")).toHaveTextContent("now at version 2."));
        fireEvent.click(screen.getByTestId("modal-btn-close"));
        expect(onClose).toHaveBeenCalledTimes(1);
        expect(onClose).toHaveBeenLastCalledWith(true);
    });

    it("clears the form, including header values, when it opens again", () => {
        const { rerender } = render(modal(vi.fn()));
        type("form-mcp-url", "https://mcp.example.com/mcp");
        fireEvent.click(screen.getByTestId("form-mcp-header-add"));
        type("form-mcp-header-value-0", SECRET);

        rerender(modal(vi.fn(), false));
        rerender(modal(vi.fn(), true));

        expect(screen.getByTestId("form-mcp-url")).toHaveValue("");
        expect(screen.queryByTestId("form-mcp-header-value-0")).not.toBeInTheDocument();
    });
});
