import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import {
    createAuthOptions,
    createEndpoint,
    httpDelete,
    httpGet,
    httpPostWithReturn,
    httpPut,
    httpPutWithReturn
} from "@utils/rest.utils.ts";
import { ArtifactMetaData } from "@models/artifactMetaData.model.ts";
import { VersionMetaData } from "@models/versionMetaData.model.ts";
import { ReferenceType } from "@models/referenceType.ts";
import { ArtifactReference } from "@models/artifactReference.model.ts";
import { Rule } from "@models/rule.model.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { CreateArtifact } from "@models/createArtifact.model.ts";
import { CreateArtifactResponse } from "@models/createArtifactResponse.model.ts";
import { CreateVersion } from "@models/createVersion.model.ts";
import { EditableVersionMetaData } from "@models/editableVersionMetaData.model.ts";
import { EditableArtifactMetaData } from "@models/editableArtifactMetaData.model.ts";
import { GroupMetaData } from "@models/groupMetaData.model.ts";
import { CreateGroup } from "@models/createGroup.model.ts";
import { EditableGroupMetaData } from "@models/editableGroupMetaData.model.ts";
import { ArtifactSearchResults } from "@models/artifactSearchResults.model.ts";
import { Paging } from "@models/paging.model.ts";
import { SortOrder } from "@models/sortOrder.model.ts";
import { ArtifactSortBy } from "@models/artifactSortBy.model.ts";
import { VersionSortBy } from "@models/versionSortBy.model.ts";
import { VersionSearchResults } from "@models/versionSearchResults.model.ts";


export interface ClientGeneration {
    clientClassName: string;
    namespaceName: string;
    includePatterns: string,
    excludePatterns: string,
    language: string;
    content: string;
}

const createGroup = async (config: ConfigService, auth: AuthService, data: CreateGroup): Promise<GroupMetaData> => {
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups", { groupId: data.groupId });
    const options = await createAuthOptions(auth);

    return httpPostWithReturn<CreateGroup, GroupMetaData>(endpoint, data, options);
};

const getGroupMetaData = async (config: ConfigService, auth: AuthService, groupId: string): Promise<GroupMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId", { groupId });
    const options = await createAuthOptions(auth);
    return httpGet<GroupMetaData>(endpoint, options);
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

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts", { groupId }, queryParams);
    const options = await createAuthOptions(auth);
    return httpGet<ArtifactSearchResults>(endpoint, options);
};

const updateGroupMetaData = async (config: ConfigService, auth: AuthService, groupId: string, metaData: EditableGroupMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId", { groupId });
    const options = await createAuthOptions(auth);
    return httpPut<EditableGroupMetaData>(endpoint, metaData, options);
};

const updateGroupOwner = async (config: ConfigService, auth: AuthService, groupId: string, newOwner: string): Promise<void> => {
    return updateGroupMetaData(config, auth, groupId, {
        owner: newOwner
    } as any);
};

const deleteGroup = async (config: ConfigService, auth: AuthService, groupId: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Deleting group:", groupId);
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId", { groupId });
    const options = await createAuthOptions(auth);
    return httpDelete(endpoint, options);
};


const createArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, data: CreateArtifact): Promise<CreateArtifactResponse> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts", { groupId });
    const options = await createAuthOptions(auth);

    return httpPostWithReturn<CreateArtifact, CreateArtifactResponse>(endpoint, data, options);
};

const createArtifactVersion = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, data: CreateVersion): Promise<VersionMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions", { groupId, artifactId });

    return httpPostWithReturn<CreateVersion, VersionMetaData>(endpoint, data, options);
};

const getArtifactMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<ArtifactMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return httpGet<ArtifactMetaData>(endpoint, options);
};

const getArtifactVersionMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:versionExpression",
        { groupId, artifactId, versionExpression });
    const options = await createAuthOptions(auth);
    return httpGet<VersionMetaData>(endpoint, options);
};

const getArtifactReferences = async (config: ConfigService, auth: AuthService, globalId: number, refType: ReferenceType): Promise<ArtifactReference[]> => {
    const queryParams: any = {
        refType: refType || "OUTBOUND"
    };
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/ids/globalIds/:globalId/references", { globalId }, queryParams);
    const options = await createAuthOptions(auth);
    return httpGet<ArtifactReference[]>(endpoint, options);
};

const getLatestArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<string> => {
    return getArtifactVersionContent(config, auth, groupId, artifactId, "latest");
};

const updateArtifactMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, metaData: EditableArtifactMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return httpPut<EditableArtifactMetaData>(endpoint, metaData, options);
};

const updateArtifactVersionMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string, metaData: EditableVersionMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:versionExpression",
        { groupId, artifactId, versionExpression });
    const options = await createAuthOptions(auth);
    return httpPut<EditableVersionMetaData>(endpoint, metaData, options);
};

const updateArtifactOwner = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, newOwner: string): Promise<void> => {
    return updateArtifactMetaData(config, auth, groupId, artifactId, {
        owner: newOwner
    } as any);
};

const getArtifactVersionContent = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<string> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:versionExpression/content",
        { groupId, artifactId, versionExpression });

    const options = await createAuthOptions(auth);
    options.headers = {
        ...options.headers,
        "Accept": "*"
    };
    options.maxContentLength = 5242880; // TODO 5MB hard-coded, make this configurable?
    options.responseType = "text";
    options.transformResponse = (data: any) => data;
    return httpGet<string>(endpoint, options);
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
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions", { groupId, artifactId }, queryParams);
    const options = await createAuthOptions(auth);
    return httpGet<VersionSearchResults>(endpoint, options);
};

const getArtifactRules = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<Rule[]> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Getting the list of rules for artifact: ", groupId, artifactId);
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules", { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return httpGet<string[]>(endpoint, options).then( ruleTypes => {
        return Promise.all(ruleTypes.map(rt => getArtifactRule(config, auth, groupId, artifactId, rt)));
    });
};

const getArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules/:rule", {
        groupId,
        artifactId,
        rule: type
    });
    const options = await createAuthOptions(auth);
    return httpGet<Rule>(endpoint, options);
};

const createArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Creating rule:", type);

    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules", { groupId, artifactId });
    const body: Rule = {
        config: configValue,
        type
    };
    const options = await createAuthOptions(auth);
    return httpPostWithReturn(endpoint, body, options);
};

const updateArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Updating rule:", type);
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules/:rule", {
        groupId,
        artifactId,
        "rule": type
    });
    const body: Rule = { config: configValue, type };
    const options = await createAuthOptions(auth);
    return httpPutWithReturn<Rule, Rule>(endpoint, body, options);
};

const deleteArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Deleting rule:", type);
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules/:rule", {
        groupId,
        artifactId,
        "rule": type
    });
    const options = await createAuthOptions(auth);
    return httpDelete(endpoint, options);
};

const deleteArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Deleting artifact:", groupId, artifactId);
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
    const options = await createAuthOptions(auth);
    return httpDelete(endpoint, options);
};


const deleteArtifactVersion = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Deleting version: ", groupId, artifactId, version);
    const baseHref: string = config.artifactsUrl();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:version", {
        groupId,
        artifactId,
        version
    });
    const options = await createAuthOptions(auth);
    return httpDelete(endpoint, options);
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

    createArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule>;
    getArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<Rule>;
    updateArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule>;
    deleteArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<void>;

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

        createArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> {
            return createArtifactRule(config, auth, groupId, artifactId, type, configValue);
        },
        getArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<Rule> {
            return getArtifactRule(config, auth, groupId, artifactId, type);
        },
        updateArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> {
            return updateArtifactRule(config, auth, groupId, artifactId, type, configValue);
        },
        deleteArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<void> {
            return deleteArtifactRule(config, auth, groupId, artifactId, type);
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
