import { describe, expect, it, vi } from "vitest";
import { handleMutatingActionError } from "./handleMutatingActionError";
import { PageErrorType } from "./PageErrorType";

describe("handleMutatingActionError", () => {
    it("clears the wait overlay and sets a page error", () => {
        const clearWait = vi.fn();
        const setPageError = vi.fn();
        const error = { status: 500, detail: "boom" };

        handleMutatingActionError(error, "Error deleting an artifact.", setPageError, clearWait);

        expect(clearWait).toHaveBeenCalledTimes(1);
        expect(setPageError).toHaveBeenCalledTimes(1);
        expect(setPageError).toHaveBeenCalledWith({
            error,
            errorMessage: "Error deleting an artifact.",
            type: PageErrorType.Server
        });
        expect(clearWait.mock.invocationCallOrder[0]).toBeLessThan(setPageError.mock.invocationCallOrder[0]);
    });

    it("still sets the page error when no clearWait callback is provided", () => {
        const setPageError = vi.fn();

        handleMutatingActionError({ status: 400 }, "Error deleting a version.", setPageError);

        expect(setPageError).toHaveBeenCalledWith({
            error: { status: 400 },
            errorMessage: "Error deleting a version.",
            type: PageErrorType.Server
        });
    });
});
