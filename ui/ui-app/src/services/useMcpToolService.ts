import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { getRegistryClient, isErrorStatus } from "@utils/rest.utils.ts";
import { AuthService, useAuth } from "@apitomy/common-ui-components";
import { Paging } from "@models/Paging.ts";
import { McpToolCompatibilityResults, McpToolCompatibilityVerdict } from "@sdk/lib/generated-client/models";


/**
 * Compares a version of an MCP tool with the other registered MCP tools.  Resolves to undefined
 * when compatibility information is unavailable: the API answers 404 both when MCP tools support
 * is disabled and when the tool does not exist, and the two cannot be told apart.
 */
const getToolCompatibility = async (config: ConfigService, auth: AuthService, groupId: string, artifactId: string,
    version: string, verdicts: McpToolCompatibilityVerdict[], paging: Paging): Promise<McpToolCompatibilityResults | undefined> => {
    console.debug("[McpToolService] Getting the compatibility of MCP tool: ", groupId, artifactId, version, verdicts, paging);
    return getRegistryClient(config, auth).wellKnown.mcpTools.byGroupId(groupId).byArtifactId(artifactId)
        .compatibility.get({
            queryParameters: {
                version,
                // Without a verdict the API returns every verdict
                verdict: verdicts.length > 0 ? verdicts : undefined,
                offset: (paging.page - 1) * paging.pageSize,
                limit: paging.pageSize
            }
        }).catch(error => {
            if (isErrorStatus(error, 404)) {
                return undefined;
            }
            throw error;
        });
};


export interface McpToolService {
    getToolCompatibility(groupId: string, artifactId: string, version: string, verdicts: McpToolCompatibilityVerdict[],
        paging: Paging): Promise<McpToolCompatibilityResults | undefined>;
}


export const useMcpToolService: () => McpToolService = (): McpToolService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        getToolCompatibility(groupId: string, artifactId: string, version: string, verdicts: McpToolCompatibilityVerdict[],
            paging: Paging): Promise<McpToolCompatibilityResults | undefined> {
            return getToolCompatibility(config, auth, groupId, artifactId, version, verdicts, paging);
        },
    };
};
