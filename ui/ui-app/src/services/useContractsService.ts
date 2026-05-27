import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { createAuthOptions, createEndpoint } from "@utils/rest.utils.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import axios from "axios";

export interface ContractMetadata {
    status?: string;
    ownerTeam?: string;
    ownerDomain?: string;
    supportContact?: string;
    classification?: string;
    stage?: string;
    stableDate?: string;
    deprecatedDate?: string;
    deprecationReason?: string;
    compatibilityGroup?: string;
}

export interface QualityScore {
    overall: number;
    completeness: number;
    compliance: number;
    stability: number;
}

export interface ContractAuditEntry {
    auditId: number;
    groupId: string;
    artifactId: string;
    version?: string;
    action: string;
    principal?: string;
    details?: string;
    createdOn: string;
}

const getContractMetadata = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string): Promise<ContractMetadata> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/metadata",
        { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return axios.get(endpoint, options).then(response => response.data);
};

const getContractQuality = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string, contractId: string): Promise<QualityScore> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/quality",
        { groupId, artifactId }, { contractId });
    const options = await createAuthOptions(auth);
    return axios.get(endpoint, options).then(response => response.data);
};

const getContractAuditLog = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string, offset: number, limit: number): Promise<ContractAuditEntry[]> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/audit",
        { groupId, artifactId }, { offset: String(offset), limit: String(limit) });
    const options = await createAuthOptions(auth);
    return axios.get(endpoint, options).then(response => response.data);
};

const updateContractMetadata = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string, metadata: ContractMetadata): Promise<ContractMetadata> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/metadata",
        { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return axios.put(endpoint, metadata, options).then(response => response.data);
};

const transitionContractStatus = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string, status: string): Promise<ContractMetadata> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/status",
        { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return axios.post(endpoint, { status }, options).then(response => response.data);
};

export interface ContractRule {
    name?: string;
    kind?: string;
    type?: string;
    mode?: string;
    expr?: string;
    onFailure?: string;
    disabled?: boolean;
}

export interface ContractRuleSet {
    domainRules?: ContractRule[];
    migrationRules?: ContractRule[];
}

const getContractRuleset = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string): Promise<ContractRuleSet> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/ruleset",
        { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return axios.get(endpoint, options).then(response => response.data);
};

const promoteContract = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string,
    contractId: string, targetStage: string): Promise<void> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/promote",
        { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return axios.post(endpoint, { contractId, targetStage }, options).then(response => response.data);
};

const getContractYaml = async (config: ConfigService, auth: AuthService,
    groupId: string | null, contractId: string): Promise<string> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/contracts/:contractId",
        { groupId, contractId });
    const options = await createAuthOptions(auth);
    return axios.get(endpoint, { ...options, headers: { ...options.headers, "Accept": "application/x-yaml" } })
        .then(response => response.data);
};

const submitContract = async (config: ConfigService, auth: AuthService,
    groupId: string | null, yaml: string): Promise<void> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/contracts",
        { groupId });
    const options = await createAuthOptions(auth);
    return axios.post(endpoint, yaml, {
        ...options,
        headers: { ...options.headers, "Content-Type": "application/x-yaml" }
    }).then(response => response.data);
};

const updateContract = async (config: ConfigService, auth: AuthService,
    groupId: string | null, contractId: string, yaml: string): Promise<void> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/contracts/:contractId",
        { groupId, contractId });
    const options = await createAuthOptions(auth);
    return axios.put(endpoint, yaml, {
        ...options,
        headers: { ...options.headers, "Content-Type": "application/x-yaml" }
    }).then(response => response.data);
};

const exportContractAsOdcs = async (config: ConfigService, auth: AuthService,
    groupId: string | null, artifactId: string): Promise<string> => {
    const baseHref: string = config.artifactsUrl();
    groupId = groupId || "default";
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/contract/export",
        { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return axios.get(endpoint, { ...options, headers: { ...options.headers, "Accept": "application/x-yaml" } })
        .then(response => response.data);
};

export type ContractsService = {
    getContractMetadata(groupId: string | null, artifactId: string): Promise<ContractMetadata>;
    getContractQuality(groupId: string | null, artifactId: string, contractId: string): Promise<QualityScore>;
    getContractAuditLog(groupId: string | null, artifactId: string, offset: number, limit: number): Promise<ContractAuditEntry[]>;
    updateContractMetadata(groupId: string | null, artifactId: string, metadata: ContractMetadata): Promise<ContractMetadata>;
    transitionContractStatus(groupId: string | null, artifactId: string, status: string): Promise<ContractMetadata>;
    getContractRuleset(groupId: string | null, artifactId: string): Promise<ContractRuleSet>;
    promoteContract(groupId: string | null, artifactId: string, contractId: string, targetStage: string): Promise<void>;
    getContractYaml(groupId: string | null, contractId: string): Promise<string>;
    submitContract(groupId: string | null, yaml: string): Promise<void>;
    updateContract(groupId: string | null, contractId: string, yaml: string): Promise<void>;
    exportContractAsOdcs(groupId: string | null, artifactId: string): Promise<string>;
};

export const useContractsService: () => ContractsService = (): ContractsService => {
    const config: ConfigService = useConfigService();
    const auth: AuthService = useAuth();

    return {
        getContractMetadata: (groupId: string | null, artifactId: string) =>
            getContractMetadata(config, auth, groupId, artifactId),
        getContractQuality: (groupId: string | null, artifactId: string, contractId: string) =>
            getContractQuality(config, auth, groupId, artifactId, contractId),
        getContractAuditLog: (groupId: string | null, artifactId: string, offset: number, limit: number) =>
            getContractAuditLog(config, auth, groupId, artifactId, offset, limit),
        updateContractMetadata: (groupId: string | null, artifactId: string, metadata: ContractMetadata) =>
            updateContractMetadata(config, auth, groupId, artifactId, metadata),
        transitionContractStatus: (groupId: string | null, artifactId: string, status: string) =>
            transitionContractStatus(config, auth, groupId, artifactId, status),
        getContractRuleset: (groupId: string | null, artifactId: string) =>
            getContractRuleset(config, auth, groupId, artifactId),
        promoteContract: (groupId: string | null, artifactId: string, contractId: string, targetStage: string) =>
            promoteContract(config, auth, groupId, artifactId, contractId, targetStage),
        getContractYaml: (groupId: string | null, contractId: string) =>
            getContractYaml(config, auth, groupId, contractId),
        submitContract: (groupId: string | null, yaml: string) =>
            submitContract(config, auth, groupId, yaml),
        updateContract: (groupId: string | null, contractId: string, yaml: string) =>
            updateContract(config, auth, groupId, contractId, yaml),
        exportContractAsOdcs: (groupId: string | null, artifactId: string) =>
            exportContractAsOdcs(config, auth, groupId, artifactId),
    };
};
