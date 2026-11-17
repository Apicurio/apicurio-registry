import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import { McpToolCompatibilityResults, McpToolCompatibilityVerdict } from "@sdk/lib/generated-client/models";

/**
 * What the compatibility panel shows.
 */
export type CompatibilityViewState = "loading" | "error" | "unavailable" | "notComparable" | "empty" | "results";

/**
 * The label of a verdict.  Each verdict has its own colour, so that an indeterminate verdict is
 * never mistaken for an incompatible one.
 */
export type VerdictLabel = {
    text: string;
    color: "green" | "red" | "orange";
};

/**
 * The compatible tools tab is shown for MCP tool versions, except disabled ones.
 */
export const showCompatibleToolsTab = (artifactType: string | null | undefined,
    versionState: string | null | undefined): boolean => {
    return artifactType === ArtifactTypes.MCP_TOOL && versionState !== "DISABLED";
};

/**
 * Selects what to show for a compatibility request.  The results are undefined when the API has
 * no compatibility information for the tool, and top-level limitations mean that the tool itself
 * could not be compared with any other.
 */
export const compatibilityViewState = (isLoading: boolean, isError: boolean,
    results: McpToolCompatibilityResults | undefined): CompatibilityViewState => {
    if (isLoading) {
        return "loading";
    }
    if (isError) {
        return "error";
    }
    if (!results) {
        return "unavailable";
    }
    if ((results.limitations?.length ?? 0) > 0) {
        return "notComparable";
    }
    if ((results.results?.length ?? 0) === 0) {
        return "empty";
    }
    return "results";
};

export const verdictLabel = (verdict: McpToolCompatibilityVerdict | null | undefined): VerdictLabel => {
    switch (verdict) {
        case "COMPATIBLE":
            return { text: "Compatible", color: "green" };
        case "INCOMPATIBLE":
            return { text: "Incompatible", color: "red" };
        default:
            return { text: "Indeterminate", color: "orange" };
    }
};

/**
 * Summarizes the verdicts of every evaluated tool, from the totals of the response.
 */
export const compatibilitySummary = (results: McpToolCompatibilityResults): string => {
    const evaluated: number = results.evaluated ?? 0;
    const totals = results.totals;
    return `Compared with ${evaluated} other MCP ${evaluated === 1 ? "tool" : "tools"}: `
        + `${totals?.compatible ?? 0} compatible, ${totals?.incompatible ?? 0} incompatible, `
        + `${totals?.indeterminate ?? 0} indeterminate.`;
};

/**
 * Says what the listed results leave out: the tools beyond the scan limit, and the results
 * beyond the page shown.
 */
export const compatibilityNotices = (results: McpToolCompatibilityResults): string[] => {
    const notices: string[] = [];
    if (results.complete === false) {
        notices.push(`Only the newest ${results.scanLimit} MCP tools were compared.`);
    }
    const shown: number = results.results?.length ?? 0;
    if ((results.count ?? 0) > shown) {
        notices.push(`Showing the first ${shown} of ${results.count} results.`);
    }
    return notices;
};
