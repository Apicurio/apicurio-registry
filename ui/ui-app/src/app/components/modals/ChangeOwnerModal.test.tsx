// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ChangeOwnerModal } from "./ChangeOwnerModal";

const onChangeOwner = vi.fn();

const modal = (isOpen: boolean) => (
    <ChangeOwnerModal isOpen={isOpen} currentOwner="alice" onClose={() => {}} onChangeOwner={onChangeOwner} />
);

const newOwnerInput = (): HTMLInputElement => screen.getByTestId("form-new-owner") as HTMLInputElement;
const changeOwnerButton = (): HTMLElement => screen.getByTestId("modal-btn-edit");

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe("ChangeOwnerModal", () => {
    it("clears a value abandoned by Cancel when it is opened again", () => {
        const { rerender } = render(modal(true));
        fireEvent.change(newOwnerInput(), { target: { value: "someone-else" } });
        expect(changeOwnerButton()).toBeEnabled();

        rerender(modal(false));
        rerender(modal(true));

        expect(newOwnerInput()).toHaveValue("");
        expect(changeOwnerButton()).toBeDisabled();
    });

    it("submits the owner typed in the current open", () => {
        render(modal(true));
        fireEvent.change(newOwnerInput(), { target: { value: "bob" } });
        fireEvent.click(changeOwnerButton());

        expect(onChangeOwner).toHaveBeenCalledTimes(1);
        expect(onChangeOwner).toHaveBeenCalledWith("bob");
    });
});
