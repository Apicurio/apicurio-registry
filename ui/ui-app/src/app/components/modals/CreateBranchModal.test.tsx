// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CreateBranchModal } from "./CreateBranchModal";

const onCreate = vi.fn();

const modal = (isOpen: boolean) => (
    <CreateBranchModal isOpen={isOpen} onClose={() => {}} onCreate={onCreate} />
);

const branchIdInput = (): HTMLElement => screen.getByTestId("create-version-branch-id");
const descriptionInput = (): HTMLElement => screen.getByTestId("create-branch-modal-description");

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("CreateBranchModal", () => {
    it("clears values abandoned by Cancel when it is opened again", () => {
        const { rerender } = render(modal(true));
        fireEvent.change(branchIdInput(), { target: { value: "feature-x" } });
        fireEvent.change(descriptionInput(), { target: { value: "abandoned" } });

        rerender(modal(false));
        rerender(modal(true));

        expect(branchIdInput()).toHaveValue("");
        expect(descriptionInput()).toHaveValue("");
    });

    it("creates the branch typed in the current open", () => {
        render(modal(true));
        fireEvent.change(branchIdInput(), { target: { value: "feature-y" } });
        fireEvent.change(descriptionInput(), { target: { value: "new work" } });
        fireEvent.click(screen.getByTestId("modal-btn-create"));

        expect(onCreate).toHaveBeenCalledTimes(1);
        expect(onCreate).toHaveBeenCalledWith({ branchId: "feature-y", description: "new work" });
    });
});
