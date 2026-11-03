// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { PeerList } from "./PeerList";

const onEdit = vi.fn();
const onRemove = vi.fn();

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

const list = (isReadOnly: boolean) => (
    <PeerList peers={[eu, us]} isReadOnly={isReadOnly} onEdit={onEdit} onRemove={onRemove} />
);

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("PeerList", () => {
    it("shows each peer with its url, status and credential reference", () => {
        render(list(false));

        const euRow = within(screen.getByTestId("peer-row-eu-west"));
        expect(euRow.getByText("EU registry")).toBeInTheDocument();
        expect(euRow.getByText("eu-west")).toBeInTheDocument();
        expect(euRow.getByText("Run by the EU team")).toBeInTheDocument();
        expect(euRow.getByText("https://registry.eu.example.com")).toBeInTheDocument();
        expect(euRow.getByText("Enabled")).toBeInTheDocument();
        expect(euRow.getByText("eu-token")).toBeInTheDocument();

        const usRow = within(screen.getByTestId("peer-row-us-east"));
        expect(usRow.getByText("us-east")).toBeInTheDocument();
        expect(usRow.getByText("Disabled")).toBeInTheDocument();
        expect(usRow.getByText("None")).toBeInTheDocument();
    });

    it("offers no actions when the UI is read-only", () => {
        render(list(true));

        expect(screen.queryByLabelText("Kebab toggle")).not.toBeInTheDocument();
    });

    it("edits the peer chosen from its row menu", () => {
        render(list(false));
        fireEvent.click(within(screen.getByTestId("peer-row-us-east")).getByLabelText("Kebab toggle"));
        fireEvent.click(screen.getByText("Edit"));

        expect(onEdit).toHaveBeenCalledTimes(1);
        expect(onEdit).toHaveBeenCalledWith(us);
    });

    it("removes the peer only after the confirmation", () => {
        render(list(false));
        fireEvent.click(within(screen.getByTestId("peer-row-eu-west")).getByLabelText("Kebab toggle"));
        fireEvent.click(screen.getByText("Remove"));

        expect(onRemove).not.toHaveBeenCalled();
        expect(screen.getByText("Remove remote registry?")).toBeInTheDocument();

        fireEvent.click(screen.getByTestId("modal-btn-remove"));

        expect(onRemove).toHaveBeenCalledTimes(1);
        expect(onRemove).toHaveBeenCalledWith(eu);
    });

    it("does not remove the peer when the confirmation is cancelled", () => {
        render(list(false));
        fireEvent.click(within(screen.getByTestId("peer-row-eu-west")).getByLabelText("Kebab toggle"));
        fireEvent.click(screen.getByText("Remove"));
        fireEvent.click(screen.getByTestId("modal-btn-cancel"));

        expect(onRemove).not.toHaveBeenCalled();
    });
});
