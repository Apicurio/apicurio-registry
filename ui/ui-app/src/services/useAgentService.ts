import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { Paging } from "@models/Paging.ts";

/**
 * Agent capabilities structure
 */
export interface AgentCapabilities {
    streaming?: boolean;
    pushNotifications?: boolean;
    stateTransitionHistory?: boolean;
}

/**
 * Agent search result structure
 */
export interface AgentSearchResult {
    groupId: string;
    artifactId: string;
    name: string;
    description?: string;
    version?: string;
    url?: string;
    skills?: string[];
    capabilities?: AgentCapabilities;
    createdOn?: number;
    owner?: string;
}

/**
 * Agent search results structure
 */
export interface AgentSearchResults {
    count: number;
    agents: AgentSearchResult[];
}

/**
 * Agent search filters
 */
export interface AgentSearchFilters {
    name?: string;
    capability?: string;
    skill?: string;
}

const searchAgents = async (
    config: ConfigService,
    auth: AuthService,
    filters: AgentSearchFilters,
    paging: Paging
): Promise<AgentSearchResults> => {
    console.debug("[AgentService] Searching agents: ", filters, paging);

    const start: number = (paging.page - 1) * paging.pageSize;
    const limit: number = paging.pageSize;

    // Build query string
    const params = new URLSearchParams();
    params.append("offset", String(start));
    params.append("limit", String(limit));

    if (filters.name) {
        params.append("name", filters.name);
    }
    if (filters.capability) {
        params.append("capability", filters.capability);
    }
    if (filters.skill) {
        params.append("skill", filters.skill);
    }

    const baseUrl = config.apiUrl() || window.location.origin;
    const url = `${baseUrl}/.well-known/agents?${params.toString()}`;

    const headers: HeadersInit = {
        "Accept": "application/json"
    };

    // Add auth header if needed
    const authHeader = auth.getAuthorizationHeader?.();
    if (authHeader) {
        headers["Authorization"] = authHeader;
    }

    const response = await fetch(url, {
        method: "GET",
        headers
    });

    if (!response.ok) {
        throw new Error(`Failed to search agents: ${response.status} ${response.statusText}`);
    }

    return response.json();
};

const getAgentCard = async (
    config: ConfigService,
    auth: AuthService,
    groupId: string,
    artifactId: string
): Promise<any> => {
    console.debug("[AgentService] Getting agent card: ", groupId, artifactId);

    const baseUrl = config.apiUrl() || window.location.origin;
    const url = `${baseUrl}/.well-known/agents/${encodeURIComponent(groupId)}/${encodeURIComponent(artifactId)}`;

    const headers: HeadersInit = {
        "Accept": "application/json"
    };

    const authHeader = auth.getAuthorizationHeader?.();
    if (authHeader) {
        headers["Authorization"] = authHeader;
    }

    const response = await fetch(url, {
        method: "GET",
        headers
    });

    if (!response.ok) {
        throw new Error(`Failed to get agent card: ${response.status} ${response.statusText}`);
    }

    return response.json();
};

const getRegistryAgentCard = async (
    config: ConfigService,
    auth: AuthService
): Promise<any> => {
    console.debug("[AgentService] Getting registry agent card");

    const baseUrl = config.apiUrl() || window.location.origin;
    const url = `${baseUrl}/.well-known/agent.json`;

    const headers: HeadersInit = {
        "Accept": "application/json"
    };

    const authHeader = auth.getAuthorizationHeader?.();
    if (authHeader) {
        headers["Authorization"] = authHeader;
    }

    const response = await fetch(url, {
        method: "GET",
        headers
    });

    if (!response.ok) {
        throw new Error(`Failed to get registry agent card: ${response.status} ${response.statusText}`);
    }

    return response.json();
};

export interface AgentService {
    searchAgents(filters: AgentSearchFilters, paging: Paging): Promise<AgentSearchResults>;
    getAgentCard(groupId: string, artifactId: string): Promise<any>;
    getRegistryAgentCard(): Promise<any>;
}

export const useAgentService: () => AgentService = (): AgentService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        searchAgents(filters: AgentSearchFilters, paging: Paging): Promise<AgentSearchResults> {
            return searchAgents(config, auth, filters, paging);
        },
        getAgentCard(groupId: string, artifactId: string): Promise<any> {
            return getAgentCard(config, auth, groupId, artifactId);
        },
        getRegistryAgentCard(): Promise<any> {
            return getRegistryAgentCard(config, auth);
        }
    };
};
