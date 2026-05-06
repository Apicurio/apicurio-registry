import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { createEndpoint, createAuthOptions, httpGet, getRegistryClient } from "@utils/rest.utils.ts";
import { ArtifactUsageMetrics, UsageSummary } from "@sdk/lib/generated-client/models";


const getUsageSummary = async (config: ConfigService, auth: AuthService): Promise<UsageSummary | null> => {
    try {
        return await getRegistryClient(config, auth).admin.usage.summary.get().then(v => v!);
    } catch {
        return null;
    }
};

const getArtifactUsageMetrics = async (config: ConfigService, auth: AuthService,
    groupId: string, artifactId: string): Promise<ArtifactUsageMetrics | null> => {
    try {
        return await getRegistryClient(config, auth).admin.usage.artifacts
            .byGroupId(groupId).byArtifactId(artifactId).get().then(v => v!);
    } catch {
        return null;
    }
};

const getConsumerVersionHeatmap = async (config: ConfigService, auth: AuthService,
    groupId: string, artifactId: string): Promise<any | null> => {
    try {
        const baseUrl = config.artifactsUrl();
        const url = createEndpoint(baseUrl, "/admin/usage/artifacts/:groupId/:artifactId/heatmap", {
            groupId: groupId,
            artifactId: artifactId
        });
        return httpGet<any>(url, createAuthOptions(auth));
    } catch {
        return null;
    }
};

const getDeprecationReadiness = async (config: ConfigService, auth: AuthService,
    groupId: string, artifactId: string, version: string): Promise<any | null> => {
    try {
        const baseUrl = config.artifactsUrl();
        const url = createEndpoint(baseUrl, "/admin/usage/artifacts/:groupId/:artifactId/versions/:version/deprecation-readiness", {
            groupId: groupId,
            artifactId: artifactId,
            version: version
        });
        return httpGet<any>(url, createAuthOptions(auth));
    } catch {
        return null;
    }
};


export interface UsageService {
    getUsageSummary(): Promise<UsageSummary | null>;
    getArtifactUsageMetrics(groupId: string, artifactId: string): Promise<ArtifactUsageMetrics | null>;
    getConsumerVersionHeatmap(groupId: string, artifactId: string): Promise<any | null>;
    getDeprecationReadiness(groupId: string, artifactId: string, version: string): Promise<any | null>;
}

export const useUsageService: () => UsageService = (): UsageService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        getUsageSummary(): Promise<UsageSummary | null> {
            return getUsageSummary(config, auth);
        },
        getArtifactUsageMetrics(groupId: string, artifactId: string): Promise<ArtifactUsageMetrics | null> {
            return getArtifactUsageMetrics(config, auth, groupId, artifactId);
        },
        getConsumerVersionHeatmap(groupId: string, artifactId: string): Promise<any | null> {
            return getConsumerVersionHeatmap(config, auth, groupId, artifactId);
        },
        getDeprecationReadiness(groupId: string, artifactId: string, version: string): Promise<any | null> {
            return getDeprecationReadiness(config, auth, groupId, artifactId, version);
        }
    };
};
