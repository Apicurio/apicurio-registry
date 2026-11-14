import { ArtifactTypes } from "@services/useArtifactTypesService.ts";
import {
    McpToolCompatibilityResults,
    McpToolCompatibilityTotals,
    McpToolCompatibilityVerdict
} from "@sdk/lib/generated-client/models";

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
 * could not be compared with any other.  Results already loaded stay shown while another verdict
 * or page loads.
 */
export const compatibilityViewState = (isLoading: boolean, isError: boolean,
    results: McpToolCompatibilityResults | undefined): CompatibilityViewState => {
    if (isError) {
        return "error";
    }
    if (!results) {
        return isLoading ? "loading" : "unavailable";
    }
    if ((results.limitations?.length ?? 0) > 0) {
        return "notComparable";
    }
    if ((results.evaluated ?? 0) === 0) {
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
 * The label of a verdict filter, with the number of evaluated tools that have that verdict.
 */
export const verdictFilterLabel = (verdict: McpToolCompatibilityVerdict,
    totals: McpToolCompatibilityTotals | null | undefined): string => {
    let count: number | null | undefined;
    switch (verdict) {
        case "COMPATIBLE":
            count = totals?.compatible;
            break;
        case "INCOMPATIBLE":
            count = totals?.incompatible;
            break;
        default:
            count = totals?.indeterminate;
    }
    return `${verdictLabel(verdict).text} (${count ?? 0})`;
};

/**
 * Says how many other tools were evaluated.
 */
export const compatibilitySummary = (results: McpToolCompatibilityResults): string => {
    const evaluated: number = results.evaluated ?? 0;
    return `Compared with ${evaluated} other MCP ${evaluated === 1 ? "tool" : "tools"}.`;
};

/**
 * Says what the results leave out: the tools beyond the scan limit.
 */
export const compatibilityNotices = (results: McpToolCompatibilityResults): string[] => {
    return results.complete === false ? [`Only the newest ${results.scanLimit} MCP tools were compared.`] : [];
};
