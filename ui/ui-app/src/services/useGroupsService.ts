import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { getRegistryClient } from "@utils/rest.utils.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { Paging } from "@models/paging.model.ts";
import {
    ArtifactMetaData,
    ArtifactReference,
    ArtifactSearchResults,
    ArtifactSortBy,
    CreateArtifact,
    CreateArtifactResponse,
    CreateGroup,
    CreateRule,
    CreateVersion,
    EditableArtifactMetaData,
    EditableGroupMetaData,
    EditableVersionMetaData,
    GroupMetaData, ReferenceType, ReferenceTypeObject,
    Rule,
    RuleType,
    SortOrder,
    VersionMetaData,
    VersionSearchResults,
    VersionSortBy
} from "@sdk/lib/generated-client/models";


const arrayDecoder: TextDecoder = new TextDecoder("utf-8");


export interface ClientGeneration {
    clientClassName: string;
    namespaceName: string;
    includePatterns: string,
    excludePatterns: string,
    language: string;
    content: string;
}

const createGroup = async (config: ConfigService, auth: AuthService, data: CreateGroup): Promise<GroupMetaData> => {
    return getRegistryClient(config, auth).groups.post(data).then(v => v!);
};

const getGroupMetaData = async (config: ConfigService, auth: AuthService, groupId: string): Promise<GroupMetaData> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).get().then(v => v!).then(d => {
        console.info("GROUP: ", d);
        console.info("GROUP Labels: ", d.labels);
        return d;
    });
};

const getGroupArtifacts = async (config: ConfigService, auth: AuthService, groupId: string, sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults> => {
    groupId = normalizeGroupId(groupId);
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start,
        order: sortOrder,
        orderby: sortBy
    };

    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.get({
        queryParameters: queryParams
    }).then(v => v!);
};

const updateGroupMetaData = async (config: ConfigService, auth: AuthService, groupId: string, metaData: EditableGroupMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).put(metaData).then(v => v!);
};

const updateGroupOwner = async (config: ConfigService, auth: AuthService, groupId: string, newOwner: string): Promise<void> => {
    return updateGroupMetaData(config, auth, groupId, {
        owner: newOwner
    } as any);
};

const deleteGroup = async (config: ConfigService, auth: AuthService, groupId: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    console.info("[GroupsService] Deleting group:", groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).delete();
};


const createArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, data: CreateArtifact): Promise<CreateArtifactResponse> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.post(data).then(v => v!);
};

const createArtifactVersion = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, data: CreateVersion): Promise<VersionMetaData> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).versions.post(data).then(v => v!);
};

const getArtifactMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<ArtifactMetaData> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).get().then(v => v!);
};

const getArtifactVersionMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData> => {
    groupId = normalizeGroupId(groupId);
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).versions
        .byVersionExpression(versionExpression).get().then(v => v!);
};

const getArtifactReferences = async (config: ConfigService, auth: AuthService, globalId: number, refType: ReferenceType): Promise<ArtifactReference[]> => {
    const queryParams: any = {
        refType: refType || ReferenceTypeObject.OUTBOUND
    };
    return getRegistryClient(config, auth).ids.globalIds.byGlobalId(globalId).references.get({
        queryParameters: queryParams
    }).then(v => v!);
};

const getLatestArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<string> => {
    return getArtifactVersionContent(config, auth, groupId, artifactId, "latest");
};

const updateArtifactMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, metaData: EditableArtifactMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).put(metaData).then(v => v!);
};

const updateArtifactVersionMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string, metaData: EditableVersionMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).versions
        .byVersionExpression(versionExpression).put(metaData).then(v => v!);
};

const updateArtifactOwner = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, newOwner: string): Promise<void> => {
    return updateArtifactMetaData(config, auth, groupId, artifactId, {
        owner: newOwner
    } as any);
};

const getArtifactVersionContent = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<string> => {
    groupId = normalizeGroupId(groupId);
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).versions
        .byVersionExpression(versionExpression).content.get({
            headers: {
                "Accept": "*"
            }
        }).then(value => {
            return arrayDecoder.decode(value!);
        });
};

const getArtifactVersions = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, sortBy: VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<VersionSearchResults> => {
    groupId = normalizeGroupId(groupId);
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start,
        order: sortOrder,
        orderby: sortBy
    };

    console.info("[GroupsService] Getting the list of versions for artifact: ", groupId, artifactId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).versions.get({
        queryParameters: queryParams
    }).then(v => v!);
};

const getArtifactRules = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<Rule[]> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Getting the list of rules for artifact: ", groupId, artifactId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).rules.get().then(ruleTypes => {
        return Promise.all(ruleTypes!.map(rt => getArtifactRule(config, auth, groupId, artifactId, rt)));
    });
};

const getArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, ruleType: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).rules.byRuleType(ruleType).get().then(v => v!);
};

const createArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, ruleType: string, configValue: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);
    console.info("[GroupsService] Creating rule:", ruleType);
    const body: CreateRule = {
        config: configValue,
        ruleType: ruleType as RuleType
    };
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).rules.post(body).then(v => v!);
};

const updateArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, ruleType: string, configValue: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);
    console.info("[GroupsService] Updating rule:", ruleType);
    const body: Rule = {
        config: configValue,
        ruleType: ruleType as RuleType
    };
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).rules
        .byRuleType(ruleType).put(body).then(v => v!);
};

const deleteArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, ruleType: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    console.info("[GroupsService] Deleting rule:", ruleType);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).rules
        .byRuleType(ruleType).delete();
};

const deleteArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    console.info("[GroupsService] Deleting artifact:", groupId, artifactId);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId).delete();
};

const deleteArtifactVersion = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);
    console.info("[GroupsService] Deleting version: ", groupId, artifactId, version);
    return getRegistryClient(config, auth).groups.byGroupId(groupId).artifacts.byArtifactId(artifactId)
        .versions.byVersionExpression(version).delete();
};

const normalizeGroupId = (groupId: string|null): string => {
    return groupId || "default";
};

export interface GroupsService {
    createGroup(data: CreateGroup): Promise<GroupMetaData>;
    getGroupMetaData(groupId: string): Promise<GroupMetaData>;
    getGroupArtifacts(groupId: string, sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults>;
    updateGroupMetaData(groupId: string, metaData: EditableGroupMetaData): Promise<void>;
    updateGroupOwner(groupId: string, newOwner: string): Promise<void>;
    deleteGroup(groupId: string): Promise<void>;

    createArtifact(groupId: string|null, data: CreateArtifact): Promise<CreateArtifactResponse>;
    getArtifactMetaData(groupId: string|null, artifactId: string): Promise<ArtifactMetaData>;
    getArtifactReferences(globalId: number, refType: ReferenceType): Promise<ArtifactReference[]>;
    getArtifactRules(groupId: string|null, artifactId: string): Promise<Rule[]>;
    getArtifactVersions(groupId: string|null, artifactId: string, sortBy: VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<VersionSearchResults>;
    getLatestArtifact(groupId: string|null, artifactId: string): Promise<string>;
    updateArtifactMetaData(groupId: string|null, artifactId: string, metaData: EditableArtifactMetaData): Promise<void>;
    updateArtifactOwner(groupId: string|null, artifactId: string, newOwner: string): Promise<void>;
    deleteArtifact(groupId: string|null, artifactId: string): Promise<void>;

    createArtifactRule(groupId: string|null, artifactId: string, ruleType: string, configValue: string): Promise<Rule>;
    getArtifactRule(groupId: string|null, artifactId: string, ruleType: string): Promise<Rule>;
    updateArtifactRule(groupId: string|null, artifactId: string, ruleType: string, configValue: string): Promise<Rule>;
    deleteArtifactRule(groupId: string|null, artifactId: string, ruleType: string): Promise<void>;

    createArtifactVersion(groupId: string|null, artifactId: string, data: CreateVersion): Promise<VersionMetaData>;
    getArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData>;
    getArtifactVersionContent(groupId: string|null, artifactId: string, version: string): Promise<string>;
    updateArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string, metaData: EditableVersionMetaData): Promise<void>;
    deleteArtifactVersion(groupId: string|null, artifactId: string, version: string): Promise<void>;
}


export const useGroupsService: () => GroupsService = (): GroupsService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        createGroup(data: CreateGroup): Promise<GroupMetaData> {
            return createGroup(config, auth, data);
        },
        getGroupMetaData(groupId: string): Promise<GroupMetaData> {
            return getGroupMetaData(config, auth, groupId);
        },
        getGroupArtifacts(groupId: string, sortBy: ArtifactSortBy, sortOrder: SortOrder, paging: Paging): Promise<ArtifactSearchResults> {
            return getGroupArtifacts(config, auth, groupId, sortBy, sortOrder, paging);
        },
        updateGroupMetaData(groupId: string, metaData: EditableGroupMetaData): Promise<void> {
            return updateGroupMetaData(config, auth, groupId, metaData);
        },
        updateGroupOwner(groupId: string, newOwner: string): Promise<void> {
            return updateGroupOwner(config, auth, groupId, newOwner);
        },
        deleteGroup(groupId: string): Promise<void> {
            return deleteGroup(config, auth, groupId);
        },

        createArtifact(groupId: string|null, data: CreateArtifact): Promise<CreateArtifactResponse> {
            return createArtifact(config, auth, groupId, data);
        },
        getArtifactMetaData(groupId: string|null, artifactId: string): Promise<ArtifactMetaData> {
            return getArtifactMetaData(config, auth, groupId, artifactId);
        },
        getArtifactReferences(globalId: number, refType: ReferenceType): Promise<ArtifactReference[]> {
            return getArtifactReferences(config, auth, globalId, refType);
        },
        getArtifactRules(groupId: string|null, artifactId: string): Promise<Rule[]> {
            return getArtifactRules(config, auth, groupId, artifactId);
        },
        getArtifactVersions(groupId: string|null, artifactId: string, sortBy: VersionSortBy, sortOrder: SortOrder, paging: Paging): Promise<VersionSearchResults> {
            return getArtifactVersions(config, auth, groupId, artifactId, sortBy, sortOrder, paging);
        },
        getLatestArtifact(groupId: string|null, artifactId: string): Promise<string> {
            return getLatestArtifact(config, auth, groupId, artifactId);
        },
        updateArtifactMetaData(groupId: string|null, artifactId: string, metaData: EditableArtifactMetaData): Promise<void> {
            return updateArtifactMetaData(config, auth, groupId, artifactId, metaData);
        },
        updateArtifactOwner(groupId: string|null, artifactId: string, newOwner: string): Promise<void> {
            return updateArtifactOwner(config, auth, groupId, artifactId, newOwner);
        },
        deleteArtifact(groupId: string|null, artifactId: string): Promise<void> {
            return deleteArtifact(config, auth, groupId, artifactId);
        },

        createArtifactRule(groupId: string|null, artifactId: string, ruleType: string, configValue: string): Promise<Rule> {
            return createArtifactRule(config, auth, groupId, artifactId, ruleType, configValue);
        },
        getArtifactRule(groupId: string|null, artifactId: string, ruleType: string): Promise<Rule> {
            return getArtifactRule(config, auth, groupId, artifactId, ruleType);
        },
        updateArtifactRule(groupId: string|null, artifactId: string, ruleType: string, configValue: string): Promise<Rule> {
            return updateArtifactRule(config, auth, groupId, artifactId, ruleType, configValue);
        },
        deleteArtifactRule(groupId: string|null, artifactId: string, ruleType: string): Promise<void> {
            return deleteArtifactRule(config, auth, groupId, artifactId, ruleType);
        },

        createArtifactVersion(groupId: string|null, artifactId: string, data: CreateVersion): Promise<VersionMetaData> {
            return createArtifactVersion(config, auth, groupId, artifactId, data);
        },
        getArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData> {
            return getArtifactVersionMetaData(config, auth, groupId, artifactId, version);
        },
        getArtifactVersionContent(groupId: string|null, artifactId: string, version: string): Promise<string> {
            return getArtifactVersionContent(config, auth, groupId, artifactId, version);
        },
        updateArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string, metaData: EditableVersionMetaData): Promise<void> {
            return updateArtifactVersionMetaData(config, auth, groupId, artifactId, version, metaData);
        },
        deleteArtifactVersion(groupId: string|null, artifactId: string, version: string): Promise<void> {
            return deleteArtifactVersion(config, auth, groupId, artifactId, version);
        },
    };
};
