// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";

const { agentMock, navigateTo } = vi.hoisted(() => {
    // useConfigService.ts reads this global at module load time (normally injected by the app's
    // config.js in a real browser).
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window.ApicurioRegistryConfig = registryConfig;
    return {
        agentMock: {
            searchAgents: vi.fn(),
            searchFederatedAgents: vi.fn(),
            getAgentCard: vi.fn(),
            getRegistryAgentCard: vi.fn()
        },
        navigateTo: vi.fn()
    };
});

vi.mock("@services/useAgentService", () => ({
    useAgentService: () => agentMock
}));

vi.mock("@services/useAppNavigation.ts", () => ({
    useAppNavigation: () => ({ navigateTo, createLink: (path: string) => path })
}));

vi.mock("@services/useGroupsService.ts", () => ({
    useGroupsService: () => ({})
}));

vi.mock("@services/useConfigService.ts", async (importOriginal) => ({
    ...(await importOriginal<any>()),
    useConfigService: () => ({ features: () => ({ readOnly: false }), featureReadOnly: () => false })
}));

vi.mock("@app/components", async (importOriginal) => ({
    ...(await importOriginal<any>()),
    RootPageHeader: () => <div data-testid="root-page-header" />
}));

vi.mock("@app/pages/agents/components", async (importOriginal) => ({
    ...(await importOriginal<any>()),
    CreateAgentModal: () => null,
    ImportAgentModal: () => null
}));

import { AgentsPage } from "./AgentsPage";

const mine = { groupId: "g", artifactId: "mine", name: "Mine", skills: ["local-skill"] };

const localOnly = { count: 1, agents: [mine] };

const federated = {
    agents: [
        { ...mine, source: "local" },
        { source: "eu-west", groupId: "remote", artifactId: "theirs", name: "Theirs", skills: ["remote-skill"] }
    ],
    sources: [
        { source: "local", outcome: "ok", count: 1, truncated: false },
        { source: "eu-west", outcome: "ok", count: 1, truncated: true },
        { source: "us-east", outcome: "failed", reason: "timeout", count: 0, truncated: false },
        { source: "slow", outcome: "deadline_exceeded", count: 0, truncated: false },
        { source: "old", outcome: "unsupported", count: 0, truncated: false }
    ]
};

const renderPage = async (): Promise<void> => {
    render(<AgentsPage />);
    await screen.findByTestId("agent-card-local-mine");
};

const includeRemoteRegistries = (): void => {
    fireEvent.click(screen.getByTestId("include-remote-registries"));
};

afterEach(() => {
    cleanup();
    vi.resetAllMocks();
});

describe("AgentsPage across registries", () => {
    it("searches only this registry until remote registries are included", async () => {
        agentMock.searchAgents.mockResolvedValue(localOnly);

        await renderPage();

        expect(agentMock.searchAgents).toHaveBeenCalledTimes(1);
        expect(agentMock.searchFederatedAgents).not.toHaveBeenCalled();
        expect(screen.queryByTestId("federated-sources")).not.toBeInTheDocument();
        expect(screen.queryByTestId("agent-source")).not.toBeInTheDocument();
    });

    it("shows every agent with its registry and the outcome of every registry", async () => {
        agentMock.searchAgents.mockResolvedValue(localOnly);
        agentMock.searchFederatedAgents.mockResolvedValue(federated);
        await renderPage();

        includeRemoteRegistries();

        await screen.findByTestId("agent-card-eu-west-theirs");
        expect(agentMock.searchFederatedAgents).toHaveBeenCalledWith(
            { name: undefined, capability: undefined, skill: undefined }, 12);
        expect(within(screen.getByTestId("agent-card-local-mine")).getByTestId("agent-source"))
            .toHaveTextContent("This registry");
        expect(within(screen.getByTestId("agent-card-eu-west-theirs")).getByTestId("agent-source"))
            .toHaveTextContent("eu-west");
        expect(screen.getByTestId("federated-source-local")).toHaveTextContent("This registry: 1");
        expect(screen.getByTestId("federated-source-eu-west")).toHaveTextContent("eu-west: 1+");
        expect(screen.getByTestId("federated-source-us-east")).toHaveTextContent("us-east: timeout");
        expect(screen.getByTestId("federated-source-slow")).toHaveTextContent("slow: out of time");
        expect(screen.getByTestId("federated-source-old")).toHaveTextContent("old: not supported");
        expect(screen.getByText(/Up to 12 agents from each registry/)).toBeInTheDocument();
    });

    it("opens the page of an agent of this registry but not of a remote one", async () => {
        agentMock.searchAgents.mockResolvedValue(localOnly);
        agentMock.searchFederatedAgents.mockResolvedValue(federated);
        await renderPage();
        includeRemoteRegistries();
        await screen.findByTestId("agent-card-eu-west-theirs");

        fireEvent.click(screen.getByTestId("agent-card-eu-west-theirs"));
        expect(navigateTo).not.toHaveBeenCalled();

        fireEvent.click(screen.getByTestId("agent-card-local-mine"));
        expect(navigateTo).toHaveBeenCalledWith("/explore/g/mine");
    });

    it("keeps the remote registries in the search when the filters change", async () => {
        agentMock.searchAgents.mockResolvedValue(localOnly);
        agentMock.searchFederatedAgents.mockResolvedValue(federated);
        await renderPage();
        includeRemoteRegistries();
        await screen.findByTestId("agent-card-eu-west-theirs");

        fireEvent.change(screen.getByPlaceholderText("Filter by skill..."), { target: { value: "remote-skill" } });
        // The text of the Search button; the search inputs have icon buttons with the same label.
        fireEvent.click(screen.getByText("Search"));

        await waitFor(() => expect(agentMock.searchFederatedAgents).toHaveBeenCalledTimes(2));
        expect(agentMock.searchFederatedAgents).toHaveBeenLastCalledWith(
            { name: undefined, capability: undefined, skill: "remote-skill" }, 12);
        expect(agentMock.searchAgents).toHaveBeenCalledTimes(1);
    });

    it("still shows the outcome of every registry when no agent matches", async () => {
        agentMock.searchAgents.mockResolvedValue(localOnly);
        agentMock.searchFederatedAgents.mockResolvedValue({
            agents: [],
            sources: [
                { source: "local", outcome: "ok", count: 0, truncated: false },
                { source: "eu-west", outcome: "failed", reason: "circuit_open", count: 0, truncated: false }
            ]
        });
        await renderPage();

        includeRemoteRegistries();

        expect(await screen.findByText("No agents found")).toBeInTheDocument();
        expect(screen.getByTestId("federated-source-local")).toHaveTextContent("This registry: 0");
        expect(screen.getByTestId("federated-source-eu-west")).toHaveTextContent("eu-west: circuit open");
    });

    it("says why remote registries could not be searched and shows this registry's agents", async () => {
        agentMock.searchAgents.mockResolvedValue(localOnly);
        agentMock.searchFederatedAgents.mockRejectedValue({
            responseStatusCode: 409,
            detail: "Federation is not enabled on this registry instance."
        });
        await renderPage();

        includeRemoteRegistries();

        expect(await screen.findByTestId("remote-problem"))
            .toHaveTextContent("Federation is not enabled on this registry instance.");
        await waitFor(() => expect(agentMock.searchAgents).toHaveBeenCalledTimes(2));
        expect(screen.getByTestId("agent-card-local-mine")).toBeInTheDocument();
        expect(screen.getByTestId("include-remote-registries")).not.toBeChecked();
        expect(screen.queryByTestId("federated-sources")).not.toBeInTheDocument();
    });
});
