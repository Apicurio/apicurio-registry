// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { AgentCardViewer, AgentProvider } from "./AgentCardViewer";

const renderWithProvider = (provider: AgentProvider | undefined): HTMLElement => {
    const { container } = render(<AgentCardViewer agentCard={{ name: "test-agent", provider }} />);
    return container;
};

afterEach(() => {
    cleanup();
});

describe("AgentCardViewer provider", () => {
    it("uses the url as the link text when the organization is absent", () => {
        renderWithProvider({ url: "https://example.com" });

        const link = screen.getByRole("link", { name: "https://example.com" });
        expect(link).toHaveAttribute("href", "https://example.com");
        expect(link.querySelector(".external-link-icon")).not.toBeNull();
    });

    it("uses the organization as the link text when both fields are set", () => {
        renderWithProvider({ organization: "IBM", url: "https://ibm.com" });

        const link = screen.getByRole("link", { name: "IBM" });
        expect(link).toHaveAttribute("href", "https://ibm.com");
    });

    it("renders the organization as plain text when the url is absent", () => {
        renderWithProvider({ organization: "IBM" });

        expect(screen.getByText("Provider")).toBeInTheDocument();
        expect(screen.getByText("IBM")).toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "IBM" })).toBeNull();
    });

    it("hides the Provider row for an empty provider object", () => {
        renderWithProvider({});

        expect(screen.queryByText("Provider")).toBeNull();
    });

    it("hides the Provider row when both fields are empty strings", () => {
        renderWithProvider({ organization: "", url: "" });

        expect(screen.queryByText("Provider")).toBeNull();
    });
});
