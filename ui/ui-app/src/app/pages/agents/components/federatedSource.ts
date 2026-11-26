import { FederatedSearchSource } from "@sdk/lib/generated-client/models";

/**
 * The source id the server gives this registry's own results.
 */
export const LOCAL_SOURCE = "local";

const REASONS: Record<string, string> = {
    unauthorized: "The registry refused the request (401 or 403).",
    unreachable: "The registry could not be reached, or its address is not allowed.",
    timeout: "The registry did not answer within the per-registry timeout.",
    peer_error: "The registry answered with a server error or a redirect.",
    invalid_response: "The registry's answer was not a valid agent search result.",
    circuit_open: "The registry has been failing repeatedly and was not called this time.",
    capacity_exceeded: "This registry did not call it: too many calls or searches were in progress, or more registries are enabled than one search queries."
};

export const SOURCE_COLORS: Record<string, "green" | "orange" | "grey" | "red"> = {
    ok: "green",
    deadline_exceeded: "orange",
    unsupported: "grey",
    failed: "red"
};

export const sourceName = (source?: string | null): string => {
    return source === LOCAL_SOURCE ? "This registry" : source || "";
};

/**
 * The short text of a source's label.
 */
export const sourceLabel = (source: FederatedSearchSource): string => {
    const name = sourceName(source.source);
    switch (source.outcome) {
        case "ok":
            return `${name}: ${source.count ?? 0}${source.truncated ? "+" : ""}`;
        case "deadline_exceeded":
            return `${name}: out of time`;
        case "unsupported":
            return `${name}: not supported`;
        default:
            return `${name}: ${(source.reason || "failed").replace(/_/g, " ")}`;
    }
};

/**
 * What a source's outcome means, shown when hovering its label.
 */
export const describeSource = (source: FederatedSearchSource): string => {
    switch (source.outcome) {
        case "ok": {
            const count = source.count ?? 0;
            const agents = `${count} agent${count === 1 ? "" : "s"}`;
            return source.truncated ? `${agents} shown; more matched than were returned.` : `${agents} found.`;
        }
        case "deadline_exceeded":
            return "The search ran out of time before this registry answered.";
        case "unsupported":
            return "This registry does not confirm the public-only search, so its answer was discarded.";
        default:
            // The reason is only given to users who may list the remote registries.
            return REASONS[source.reason || ""] || "The registry could not be searched. Only an administrator is shown why.";
    }
};
