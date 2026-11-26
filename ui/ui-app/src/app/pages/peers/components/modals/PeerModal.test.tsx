// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PeerModal } from "./PeerModal";

const onSave = vi.fn();
const onClose = vi.fn();

const modal = (isOpen: boolean, peer?: any) => (
    <PeerModal isOpen={isOpen} peer={peer} onClose={onClose} onSave={onSave} />
);

const type = (testId: string, value: string): void => {
    fireEvent.change(screen.getByTestId(testId), { target: { value } });
};

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("PeerModal when adding", () => {
    it("saves a new peer with only the optional fields that were filled in", () => {
        onSave.mockResolvedValue(undefined);
        render(modal(true));
        type("peer-id", "eu-west");
        type("peer-url", "https://registry.eu.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(onSave).toHaveBeenCalledTimes(1);
        expect(onSave).toHaveBeenCalledWith({
            peerId: "eu-west",
            url: "https://registry.eu.example.com",
            name: undefined,
            description: undefined,
            credentialSecretRef: undefined,
            enabled: true
        }, false);
    });

    it("sends the name, description, credential reference and the disabled state when they are set", () => {
        onSave.mockResolvedValue(undefined);
        render(modal(true));
        type("peer-id", "eu-west");
        type("peer-url", "https://registry.eu.example.com");
        type("peer-name", " EU registry ");
        type("peer-description", "Registry run by the EU team");
        type("peer-credential-ref", "eu-token");
        fireEvent.click(screen.getByTestId("peer-enabled"));
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(onSave).toHaveBeenCalledWith({
            peerId: "eu-west",
            url: "https://registry.eu.example.com",
            name: "EU registry",
            description: "Registry run by the EU team",
            credentialSecretRef: "eu-token",
            enabled: false
        }, false);
    });

    it("does not save and shows what is wrong when the form is invalid", () => {
        render(modal(true));
        type("peer-id", "EU West");
        type("peer-url", "registry.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(onSave).not.toHaveBeenCalled();
        expect(screen.getByText(/can only contain lowercase letters, digits/)).toBeInTheDocument();
        expect(screen.getByText(/Enter an absolute URL/)).toBeInTheDocument();
    });

    it("does not accept the reserved peer id", () => {
        render(modal(true));
        type("peer-id", "local");
        type("peer-url", "https://registry.eu.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(onSave).not.toHaveBeenCalled();
        expect(screen.getByText("The peer ID 'local' is reserved for this registry.")).toBeInTheDocument();
    });

    it("shows the server's message and stays open when the save is rejected", async () => {
        onSave.mockRejectedValue({ detail: "A peer with id 'eu-west' already exists." });
        render(modal(true));
        type("peer-id", "eu-west");
        type("peer-url", "https://registry.eu.example.com");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        await waitFor(() => {
            expect(screen.getByTestId("peer-save-error")).toHaveTextContent("A peer with id 'eu-west' already exists.");
        });
        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByTestId("modal-btn-save")).toBeEnabled();
    });

    it("clears values abandoned by Cancel when it is opened again", () => {
        const { rerender } = render(modal(true));
        type("peer-id", "abandoned");
        type("peer-url", "https://abandoned.example.com");

        rerender(modal(false));
        rerender(modal(true));

        expect(screen.getByTestId("peer-id")).toHaveValue("");
        expect(screen.getByTestId("peer-url")).toHaveValue("");
    });
});

describe("PeerModal when editing", () => {
    const peer = {
        peerId: "eu-west",
        url: "https://registry.eu.example.com",
        name: "EU registry",
        description: "Run by the EU team",
        credentialSecretRef: "eu-token",
        enabled: false
    };

    it("says that searches do not use the credential reference yet", () => {
        render(modal(true, peer));

        expect(screen.getByText(/Federated search does not use it yet: remote registries are searched\s+anonymously, for their public agents only\./))
            .toBeInTheDocument();
    });

    it("shows the peer with its id locked", () => {
        render(modal(true, peer));

        expect(screen.getByText("Edit remote registry")).toBeInTheDocument();
        expect(screen.getByTestId("peer-id")).toHaveValue("eu-west");
        expect(screen.getByTestId("peer-id")).toBeDisabled();
        expect(screen.getByTestId("peer-url")).toHaveValue("https://registry.eu.example.com");
        expect(screen.getByTestId("peer-name")).toHaveValue("EU registry");
        expect(screen.getByTestId("peer-credential-ref")).toHaveValue("eu-token");
        expect(screen.getByTestId("peer-enabled")).not.toBeChecked();
    });

    it("saves the changed url and omits the optional fields that were cleared", () => {
        onSave.mockResolvedValue(undefined);
        render(modal(true, peer));
        type("peer-url", "https://registry2.eu.example.com");
        type("peer-description", "");
        type("peer-credential-ref", "");
        fireEvent.click(screen.getByTestId("modal-btn-save"));

        expect(onSave).toHaveBeenCalledWith({
            peerId: "eu-west",
            url: "https://registry2.eu.example.com",
            name: "EU registry",
            description: undefined,
            credentialSecretRef: undefined,
            enabled: false
        }, true);
    });
});
