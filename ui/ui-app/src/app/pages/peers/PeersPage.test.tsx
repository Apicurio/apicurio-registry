// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";

const { adminMock, configMock } = vi.hoisted(() => {
    // useConfigService.ts reads this global at module load time (normally injected by the app's
    // config.js in a real browser).
    const registryConfig = { artifacts: { url: "http://localhost:8080/apis/registry/v3/" } };
    (globalThis as any).ApicurioRegistryConfig = registryConfig;
    (globalThis as any).window.ApicurioRegistryConfig = registryConfig;
    return {
        adminMock: {
            getPeers: vi.fn(),
            createPeer: vi.fn(),
            updatePeer: vi.fn(),
            deletePeer: vi.fn()
        },
        configMock: { readOnly: false }
    };
});

vi.mock("@services/useAdminService.ts", () => ({
    useAdminService: () => adminMock
}));

vi.mock("@services/useConfigService.ts", async (importOriginal) => ({
    ...(await importOriginal<any>()),
    useConfigService: () => ({ featureReadOnly: () => configMock.readOnly })
}));

vi.mock("@services/useUserService.ts", async (importOriginal) => ({
    ...(await importOriginal<any>()),
    useUserService: () => ({
        updateCurrentUser: () => Promise.resolve({}),
        currentUser: () => ({}),
        isUserAdmin: () => true,
        isUserDeveloper: () => true,
        isUserId: () => false
    })
}));

vi.mock("@app/components", async (importOriginal) => ({
    ...(await importOriginal<any>()),
    RootPageHeader: () => <div data-testid="root-page-header" />
}));

import { PeersPage } from "./PeersPage";

const eu = {
    peerId: "eu-west",
    url: "https://registry.eu.example.com",
    name: "EU registry",
    description: "Run by the EU team",
    credentialSecretRef: "eu-token",
    enabled: true
};
const us = {
    peerId: "us-east",
    url: "https://registry.us.example.com",
    enabled: false
};

const type = (testId: string, value: string): void => {
    fireEvent.change(screen.getByTestId(testId), { target: { value } });
};

const openRowMenu = (peerId: string): void => {
    fireEvent.click(within(screen.getByTestId(`peer-row-${peerId}`)).getByLabelText("Kebab toggle"));
};

beforeEach(() => {
    configMock.readOnly = false;
});

afterEach(() => {
    cleanup();
    vi.resetAllMocks();
});

describe("PeersPage", () => {
    it("lists the remote registries the server returned", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 2, peers: [eu, us] });
        render(<PeersPage />);

        expect(await screen.findByTestId("peer-row-eu-west")).toBeInTheDocument();
        expect(screen.getByTestId("peer-row-us-east")).toBeInTheDocument();
        expect(adminMock.getPeers).toHaveBeenCalledWith({ page: 1, pageSize: 100 });
        expect(screen.queryByTestId("peers-truncated")).not.toBeInTheDocument();
    });

    it("explains that federation is not enabled when the server answers with a conflict", async () => {
        adminMock.getPeers.mockRejectedValue({ responseStatusCode: 409, detail: "Federation is not enabled on this registry instance." });
        render(<PeersPage />);

        expect(await screen.findByTestId("federation-disabled")).toHaveTextContent("apicurio.federation.enabled");
        expect(screen.queryByTestId("btn-add-peer")).not.toBeInTheDocument();
        expect(screen.queryByTestId("btn-add-peer-empty")).not.toBeInTheDocument();
    });

    it("invites the administrator to add a first remote registry", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 0, peers: [] });
        render(<PeersPage />);

        fireEvent.click(await screen.findByTestId("btn-add-peer-empty"));

        expect(screen.getByText("Add remote registry", { selector: "h1 *, h1" })).toBeInTheDocument();
    });

    it("adds a remote registry and shows the refreshed list", async () => {
        adminMock.getPeers
            .mockResolvedValueOnce({ count: 0, peers: [] })
            .mockResolvedValueOnce({ count: 1, peers: [eu] });
        adminMock.createPeer.mockResolvedValue(undefined);
        render(<PeersPage />);

        fireEvent.click(await screen.findByTestId("btn-add-peer-empty"));
        type("peer-id", "eu-west");
        type("peer-url", "https://registry.eu.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(await screen.findByTestId("peer-row-eu-west")).toBeInTheDocument();
        expect(adminMock.createPeer).toHaveBeenCalledTimes(1);
        expect(adminMock.createPeer).toHaveBeenCalledWith({
            peerId: "eu-west",
            url: "https://registry.eu.example.com",
            name: undefined,
            description: undefined,
            credentialSecretRef: undefined,
            enabled: true
        });
        expect(adminMock.getPeers).toHaveBeenCalledTimes(2);
        expect(screen.queryByTestId("peer-id")).not.toBeInTheDocument();
    });

    it("keeps the dialog open with the server's message when the peer cannot be added", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 0, peers: [] });
        adminMock.createPeer.mockRejectedValue({ detail: "The registry storage is read-only." });
        render(<PeersPage />);

        fireEvent.click(await screen.findByTestId("btn-add-peer-empty"));
        type("peer-id", "eu-west");
        type("peer-url", "https://registry.eu.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(await screen.findByTestId("peer-save-error")).toHaveTextContent("The registry storage is read-only.");
        expect(adminMock.getPeers).toHaveBeenCalledTimes(1);
    });

    it("updates a remote registry by sending everything but its id", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 1, peers: [eu] });
        adminMock.updatePeer.mockResolvedValue(undefined);
        render(<PeersPage />);

        await screen.findByTestId("peer-row-eu-west");
        openRowMenu("eu-west");
        fireEvent.click(screen.getByText("Edit"));
        type("peer-url", "https://registry2.eu.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        await waitFor(() => expect(adminMock.updatePeer).toHaveBeenCalledTimes(1));
        expect(adminMock.updatePeer).toHaveBeenCalledWith("eu-west", {
            url: "https://registry2.eu.example.com",
            name: "EU registry",
            description: "Run by the EU team",
            credentialSecretRef: "eu-token",
            enabled: true
        });
        expect(adminMock.createPeer).not.toHaveBeenCalled();
    });

    it("removes a remote registry after the confirmation and refreshes the list", async () => {
        adminMock.getPeers
            .mockResolvedValueOnce({ count: 2, peers: [eu, us] })
            .mockResolvedValueOnce({ count: 1, peers: [us] });
        adminMock.deletePeer.mockResolvedValue(undefined);
        render(<PeersPage />);

        await screen.findByTestId("peer-row-eu-west");
        openRowMenu("eu-west");
        fireEvent.click(screen.getByText("Remove"));
        fireEvent.click(screen.getByTestId("modal-btn-remove"));

        await waitFor(() => expect(screen.queryByTestId("peer-row-eu-west")).not.toBeInTheDocument());
        expect(adminMock.deletePeer).toHaveBeenCalledWith("eu-west");
        expect(screen.getByTestId("peer-row-us-east")).toBeInTheDocument();
    });

    it("shows the server's message when a remote registry cannot be removed", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 1, peers: [eu] });
        adminMock.deletePeer.mockRejectedValue({ detail: "The registry storage is read-only." });
        render(<PeersPage />);

        await screen.findByTestId("peer-row-eu-west");
        openRowMenu("eu-west");
        fireEvent.click(screen.getByText("Remove"));
        fireEvent.click(screen.getByTestId("modal-btn-remove"));

        expect(await screen.findByTestId("peers-action-error")).toHaveTextContent("The registry storage is read-only.");
        expect(screen.getByTestId("peer-row-eu-west")).toBeInTheDocument();
    });

    it("lets the user dismiss the removal error", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 1, peers: [eu] });
        adminMock.deletePeer.mockRejectedValue({ detail: "The registry storage is read-only." });
        render(<PeersPage />);

        await screen.findByTestId("peer-row-eu-west");
        openRowMenu("eu-west");
        fireEvent.click(screen.getByText("Remove"));
        fireEvent.click(screen.getByTestId("modal-btn-remove"));
        await screen.findByTestId("peers-action-error");
        fireEvent.click(screen.getByLabelText("Dismiss"));

        expect(screen.queryByTestId("peers-action-error")).not.toBeInTheDocument();
    });

    it("offers no way to change the list when the UI is read-only", async () => {
        configMock.readOnly = true;
        adminMock.getPeers.mockResolvedValue({ count: 1, peers: [eu] });
        render(<PeersPage />);

        await screen.findByTestId("peer-row-eu-west");
        expect(screen.queryByTestId("btn-add-peer")).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Kebab toggle")).not.toBeInTheDocument();
    });

    it("does not offer to add a first remote registry when the UI is read-only", async () => {
        configMock.readOnly = true;
        adminMock.getPeers.mockResolvedValue({ count: 0, peers: [] });
        render(<PeersPage />);

        await screen.findByTestId("no-peers");
        expect(screen.queryByTestId("btn-add-peer-empty")).not.toBeInTheDocument();
    });

    it("says so when only part of the list is shown", async () => {
        adminMock.getPeers.mockResolvedValue({ count: 120, peers: [eu, us] });
        render(<PeersPage />);

        expect(await screen.findByTestId("peers-truncated")).toHaveTextContent("Showing the first 2 of 120 remote registries.");
    });
});
