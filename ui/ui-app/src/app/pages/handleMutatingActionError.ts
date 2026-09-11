import { PageError } from "@app/pages/PageError.ts";
import { toPageError } from "@app/pages/toPageError.ts";

export type ClearWaitFn = () => void;

/**
 * Clears a blocking wait/progress overlay and records a page-level error.
 * Use in mutating-action `.catch` handlers so a failed request cannot leave the modal stuck.
 */
export const handleMutatingActionError = (
    error: any,
    errorMessage: string,
    setPageError: (pageError: PageError) => void,
    clearWait?: ClearWaitFn
): void => {
    clearWait?.();
    setPageError(toPageError(error, errorMessage));
};
