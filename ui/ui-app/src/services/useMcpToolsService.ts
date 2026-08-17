import { ConfigService, useConfigService } from "./useConfigService";
import { AuthService, useAuth } from "@apitomy/common-ui-components";
import { createAuthOptions } from "../utils/rest.utils";

export interface McpToolSearchResult {
    groupId: string;
    artifactId: string;
    name: string;
    title?: string;
    description?: string;
    owner?: string;
    createdOn?: number;
    parameters?: string[];
}

export interface McpToolSearchResults {
    count: number;
    tools: McpToolSearchResult[];
}

/**
 * Gets the base URL for well-known endpoints.
 * Extracts the base URL from the artifacts URL by removing the /apis/registry/v3 suffix.
 */
export const getBaseUrl = (config: ConfigService): string => {
    const artifactsUrl = config.artifactsUrl();
    const url = artifactsUrl.endsWith("/") ? artifactsUrl.slice(0, -1) : artifactsUrl;
    const suffix = "/apis/registry/v3";
    if (url.endsWith(suffix)) {
        return url.slice(0, -suffix.length);
    }
    try {
        const parsed = new URL(url);
        return parsed.origin;
    } catch {
        return window.location.origin;
    }
};

const getCompatibleMcpTools = async (
    config: ConfigService,
    auth: AuthService,
    groupId: string,
    artifactId: string,
    version?: string
): Promise<McpToolSearchResults> => {
    console.debug("[McpToolsService] Fetching compatible MCP tools for: ", groupId, artifactId, version);

    const baseUrl = getBaseUrl(config);
    const versionQuery = version ? `?version=${encodeURIComponent(version)}` : "";
    const url = `${baseUrl}/.well-known/mcp-tools/${encodeURIComponent(groupId)}/${encodeURIComponent(artifactId)}/compatible${versionQuery}`;

    const authOptions = await createAuthOptions(auth);
    const headers: Record<string, string> = {
        "Accept": "application/json"
    };
    if (authOptions.headers) {
        Object.entries(authOptions.headers).forEach(([key, value]) => {
            if (typeof value === "string") {
                headers[key] = value;
            }
        });
    }

    const response = await fetch(url, {
        method: "GET",
        headers
    });

    if (!response.ok) {
        throw new Error(`Failed to get compatible MCP tools: ${response.status} ${response.statusText}`);
    }

    return response.json();
};

export interface McpToolsService {
    getCompatibleMcpTools(groupId: string, artifactId: string, version?: string): Promise<McpToolSearchResults>;
}

export const useMcpToolsService: () => McpToolsService = (): McpToolsService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        getCompatibleMcpTools(groupId: string, artifactId: string, version?: string): Promise<McpToolSearchResults> {
            return getCompatibleMcpTools(config, auth, groupId, artifactId, version);
        }
    };
};
