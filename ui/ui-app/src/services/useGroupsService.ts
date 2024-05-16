import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import {
    createEndpoint,
    createHeaders,
    createOptions,
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
import { SearchedVersion } from "@models/searchedVersion.model.ts";
import { Rule } from "@models/rule.model.ts";
import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { contentType } from "@utils/content.utils.ts";
import { CreateArtifact } from "@models/createArtifact.model.ts";
import { CreateArtifactResponse } from "@models/createArtifactResponse.model.ts";
import { CreateVersion } from "@models/createVersion.model.ts";
import { EditableMetaData } from "@models/editableMetaData.model.ts";
import { ArtifactSearchResults } from "@models/artifactSearchResults.model.ts";


export interface CreateArtifactData {
    groupId: string;
    id: string|null;
    type: string;
    fromURL?: string|null;
    sha?: string|null;
    content?: string|null;
    contentType?: string|null;
}

export interface CreateVersionData {
    type: string;
    content: string;
}

export interface GetArtifactsCriteria {
    type: string;
    value: string;
    sortAscending: boolean;
}

export interface Paging {
    page: number;
    pageSize: number;
}

export interface ClientGeneration {
    clientClassName: string;
    namespaceName: string;
    includePatterns: string,
    excludePatterns: string,
    language: string;
    content: string;
}


const createArtifact = async (config: ConfigService, auth: AuthService, data: CreateArtifactData): Promise<CreateArtifactResponse> => {
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts", { groupId: data.groupId });

    const body: CreateArtifact = {
        artifactId: data.id ? data.id : undefined,
        type: data.type,
        firstVersion: {
            content: {
                content: data.content as string,
                contentType: data.contentType ? data.contentType : "application/json",
            }
        }
    };

    const headers: any = createHeaders(token);
    return httpPostWithReturn<CreateArtifact, CreateArtifactResponse>(endpoint, body, createOptions(headers));
};

const createArtifactVersion = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, data: CreateVersionData): Promise<VersionMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions", { groupId, artifactId });
    const headers: any = createHeaders(token);
    const ct: string = contentType(data.type, data.content);
    const createVersion: CreateVersion = {
        content: {
            content: data.content,
            contentType: ct
        }
    };

    return httpPostWithReturn<CreateVersion, VersionMetaData>(endpoint, createVersion, createOptions(headers));
};

const getArtifacts = async (config: ConfigService, auth: AuthService, criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactSearchResults> => {
    console.debug("[GroupsService] Getting artifacts: ", criteria, paging);
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start,
        order: criteria.sortAscending ? "asc" : "desc",
        orderby: "name"
    };
    if (criteria.value) {
        if (criteria.type == "everything") {
            queryParams["name"] = criteria.value;
            queryParams["description"] = criteria.value;
            queryParams["labels"] = criteria.value;
        } else {
            queryParams[criteria.type] = criteria.value;
        }
    }
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/search/artifacts", {}, queryParams);
    const options = createOptions(createHeaders(token));
    return httpGet<ArtifactSearchResults>(endpoint, options);
};

const getArtifactMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<ArtifactMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
    const options = createOptions(createHeaders(token));
    return httpGet<ArtifactMetaData>(endpoint, options);
};

const getArtifactVersionMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:versionExpression",
        { groupId, artifactId, versionExpression });
    const options = createOptions(createHeaders(token));
    return httpGet<VersionMetaData>(endpoint, options);
};

const getArtifactReferences = async (config: ConfigService, auth: AuthService, globalId: number, refType: ReferenceType): Promise<ArtifactReference[]> => {
    const queryParams: any = {
        refType: refType || "OUTBOUND"
    };
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/ids/globalIds/:globalId/references", { globalId }, queryParams);
    const options = createOptions(createHeaders(token));
    return httpGet<ArtifactReference[]>(endpoint, options);
};

const getLatestArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<string> => {
    return getArtifactVersionContent(config, auth, groupId, artifactId, "latest");
};

const updateArtifactMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, metaData: EditableMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
    const options = createOptions(createHeaders(token));
    return httpPut<EditableMetaData>(endpoint, metaData, options);
};

const updateArtifactVersionMetaData = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string, metaData: EditableMetaData): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:versionExpression",
        { groupId, artifactId, versionExpression });
    const options = createOptions(createHeaders(token));
    return httpPut<EditableMetaData>(endpoint, metaData, options);
};

const updateArtifactOwner = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, newOwner: string): Promise<void> => {
    return updateArtifactMetaData(config, auth, groupId, artifactId, {
        owner: newOwner
    } as any);
};

const getArtifactVersionContent = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, version: string): Promise<string> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const versionExpression: string = (version == "latest") ? "branch=latest" : version;
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions/:versionExpression/content",
        { groupId, artifactId, versionExpression });

    const headers: any = createHeaders(token);
    headers["Accept"] = "*";
    const options = createOptions(headers);
    options.maxContentLength = 5242880; // TODO 5MB hard-coded, make this configurable?
    options.responseType = "text";
    options.transformResponse = (data: any) => data;
    return httpGet<string>(endpoint, options);
};

const getArtifactVersions = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<SearchedVersion[]> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Getting the list of versions for artifact: ", groupId, artifactId);
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/versions", { groupId, artifactId }, {
        limit: 500,
        offset: 0
    });
    const options = createOptions(createHeaders(token));
    return httpGet<SearchedVersion[]>(endpoint, options, (data) => {
        return data.versions;
    });
};

const getArtifactRules = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<Rule[]> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Getting the list of rules for artifact: ", groupId, artifactId);
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules", { groupId, artifactId });
    const options = createOptions(createHeaders(token));
    return httpGet<string[]>(endpoint, options).then( ruleTypes => {
        return Promise.all(ruleTypes.map(rt => getArtifactRule(config, auth, groupId, artifactId, rt)));
    });
};

const getArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules/:rule", {
        groupId,
        artifactId,
        rule: type
    });
    const options = createOptions(createHeaders(token));
    return httpGet<Rule>(endpoint, options);
};

const createArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Creating rule:", type);

    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules", { groupId, artifactId });
    const body: Rule = {
        config: configValue,
        type
    };
    const options = createOptions(createHeaders(token));
    return httpPostWithReturn(endpoint, body, options);
};

const updateArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Updating rule:", type);
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules/:rule", {
        groupId,
        artifactId,
        "rule": type
    });
    const body: Rule = { config: configValue, type };
    const options = createOptions(createHeaders(token));
    return httpPutWithReturn<Rule, Rule>(endpoint, body, options);
};

const deleteArtifactRule = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string, type: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Deleting rule:", type);
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId/rules/:rule", {
        groupId,
        artifactId,
        "rule": type
    });
    const options = createOptions(createHeaders(token));
    return httpDelete(endpoint, options);
};

const deleteArtifact = async (config: ConfigService, auth: AuthService, groupId: string|null, artifactId: string): Promise<void> => {
    groupId = normalizeGroupId(groupId);

    console.info("[GroupsService] Deleting artifact:", groupId, artifactId);
    const baseHref: string = config.artifactsUrl();
    const token: string | undefined = await auth.getToken();
    const endpoint: string = createEndpoint(baseHref, "/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
    const options = createOptions(createHeaders(token));
    return httpDelete(endpoint, options);
};

const normalizeGroupId = (groupId: string|null): string => {
    return groupId || "default";
};


export interface GroupsService {
    createArtifact(data: CreateArtifactData): Promise<CreateArtifactResponse>;
    createArtifactVersion(groupId: string|null, artifactId: string, data: CreateVersionData): Promise<VersionMetaData>;
    getArtifacts(criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactSearchResults>;
    getArtifactMetaData(groupId: string|null, artifactId: string): Promise<ArtifactMetaData>;
    getArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData>;
    getArtifactReferences(globalId: number, refType: ReferenceType): Promise<ArtifactReference[]>;
    getLatestArtifact(groupId: string|null, artifactId: string): Promise<string>;
    updateArtifactMetaData(groupId: string|null, artifactId: string, metaData: EditableMetaData): Promise<void>;
    updateArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string, metaData: EditableMetaData): Promise<void>;
    updateArtifactOwner(groupId: string|null, artifactId: string, newOwner: string): Promise<void>;
    getArtifactVersionContent(groupId: string|null, artifactId: string, version: string): Promise<string>;
    getArtifactVersions(groupId: string|null, artifactId: string): Promise<SearchedVersion[]>;
    getArtifactRules(groupId: string|null, artifactId: string): Promise<Rule[]>;
    getArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<Rule>;
    createArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule>;
    updateArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule>;
    deleteArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<void>;
    deleteArtifact(groupId: string|null, artifactId: string): Promise<void>;
}


export const useGroupsService: () => GroupsService = (): GroupsService => {
    const config: ConfigService = useConfigService();
    const auth = useAuth();

    return {
        createArtifact(data: CreateArtifactData): Promise<CreateArtifactResponse> {
            return createArtifact(config, auth, data);
        },
        createArtifactVersion(groupId: string|null, artifactId: string, data: CreateVersionData): Promise<VersionMetaData> {
            return createArtifactVersion(config, auth, groupId, artifactId, data);
        },
        getArtifacts(criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactSearchResults> {
            return getArtifacts(config, auth, criteria, paging);
        },
        getArtifactMetaData(groupId: string|null, artifactId: string): Promise<ArtifactMetaData> {
            return getArtifactMetaData(config, auth, groupId, artifactId);
        },
        getArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string): Promise<VersionMetaData> {
            return getArtifactVersionMetaData(config, auth, groupId, artifactId, version);
        },
        getArtifactReferences(globalId: number, refType: ReferenceType): Promise<ArtifactReference[]> {
            return getArtifactReferences(config, auth, globalId, refType);
        },
        getLatestArtifact(groupId: string|null, artifactId: string): Promise<string> {
            return getLatestArtifact(config, auth, groupId, artifactId);
        },
        updateArtifactMetaData(groupId: string|null, artifactId: string, metaData: EditableMetaData): Promise<void> {
            return updateArtifactMetaData(config, auth, groupId, artifactId, metaData);
        },
        updateArtifactVersionMetaData(groupId: string|null, artifactId: string, version: string, metaData: EditableMetaData): Promise<void> {
            return updateArtifactVersionMetaData(config, auth, groupId, artifactId, version, metaData);
        },
        updateArtifactOwner(groupId: string|null, artifactId: string, newOwner: string): Promise<void> {
            return updateArtifactOwner(config, auth, groupId, artifactId, newOwner);
        },
        getArtifactVersionContent(groupId: string|null, artifactId: string, version: string): Promise<string> {
            return getArtifactVersionContent(config, auth, groupId, artifactId, version);
        },
        getArtifactVersions(groupId: string|null, artifactId: string): Promise<SearchedVersion[]> {
            return getArtifactVersions(config, auth, groupId, artifactId);
        },
        getArtifactRules(groupId: string|null, artifactId: string): Promise<Rule[]> {
            return getArtifactRules(config, auth, groupId, artifactId);
        },
        getArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<Rule> {
            return getArtifactRule(config, auth, groupId, artifactId, type);
        },
        createArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> {
            return createArtifactRule(config, auth, groupId, artifactId, type, configValue);
        },
        updateArtifactRule(groupId: string|null, artifactId: string, type: string, configValue: string): Promise<Rule> {
            return updateArtifactRule(config, auth, groupId, artifactId, type, configValue);
        },
        deleteArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<void> {
            return deleteArtifactRule(config, auth, groupId, artifactId, type);
        },
        deleteArtifact(groupId: string|null, artifactId: string): Promise<void> {
            return deleteArtifact(config, auth, groupId, artifactId);
        }
    };
};
