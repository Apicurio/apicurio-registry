import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { getRegistryClient } from "@utils/rest.utils.ts";
import { ArtifactUsageMetrics, ConsumerVersionHeatmap, DeprecationReadiness, UsageSummary } from "@sdk/lib/generated-client/models";


const getUsageSummary = async (config: ConfigService, auth: AuthService): Promise<UsageSummary | null> => {
    try {
        return await getRegistryClient(config, auth).admin.usage.summary.get().then(v => v!);
    } catch (err) {
        console.debug("[UsageService] Usage summary not available:", err);
        return null;
    }
};

const getArtifactUsageMetrics = async (config: ConfigService, auth: AuthService,
    groupId: string, artifactId: string): Promise<ArtifactUsageMetrics | null> => {
    try {
        return await getRegistryClient(config, auth).admin.usage.artifacts
            .byGroupId(groupId).byArtifactId(artifactId).get().then(v => v!);
    } catch (err) {
        console.debug("[UsageService] Artifact usage metrics not available:", err);
        return null;
    }
};

const getConsumerVersionHeatmap = async (config: ConfigService, auth: AuthService,
    groupId: string, artifactId: string): Promise<ConsumerVersionHeatmap | null> => {
    try {
        return await getRegistryClient(config, auth).admin.usage.artifacts
            .byGroupId(groupId).byArtifactId(artifactId).heatmap.get().then(v => v!);
    } catch (err) {
        console.debug("[UsageService] Heatmap not available:", err);
        return null;
    }
};

const getDeprecationReadiness = async (config: ConfigService, auth: AuthService,
    groupId: string, artifactId: string, version: string): Promise<DeprecationReadiness | null> => {
    try {
        return await getRegistryClient(config, auth).admin.usage.artifacts
            .byGroupId(groupId).byArtifactId(artifactId).versions
            .byVersion(version).deprecationReadiness.get().then(v => v!);
    } catch (err) {
        console.debug("[UsageService] Deprecation readiness not available:", err);
        return null;
    }
};


export interface UsageService {
    getUsageSummary(): Promise<UsageSummary | null>;
    getArtifactUsageMetrics(groupId: string, artifactId: string): Promise<ArtifactUsageMetrics | null>;
    getConsumerVersionHeatmap(groupId: string, artifactId: string): Promise<ConsumerVersionHeatmap | null>;
    getDeprecationReadiness(groupId: string, artifactId: string, version: string): Promise<DeprecationReadiness | null>;
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
        getConsumerVersionHeatmap(groupId: string, artifactId: string): Promise<ConsumerVersionHeatmap | null> {
            return getConsumerVersionHeatmap(config, auth, groupId, artifactId);
        },
        getDeprecationReadiness(groupId: string, artifactId: string, version: string): Promise<DeprecationReadiness | null> {
            return getDeprecationReadiness(config, auth, groupId, artifactId, version);
        }
    };
};
