import { AuthService, useAuth } from "@apicurio/common-ui-components";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { ArtifactTypeInfo } from "@models/artifactTypeInfo.model.ts";
import { Rule } from "@models/rule.model.ts";
import { RoleMapping } from "@models/roleMapping.model.ts";
import { DownloadRef } from "@models/downloadRef.model.ts";
import { ConfigurationProperty } from "@models/configurationProperty.model.ts";
import { UpdateConfigurationProperty } from "@models/updateConfigurationProperty.model.ts";
import {
    createAuthOptions,
    createEndpoint,
    httpDelete,
    httpGet, httpPost,
    httpPostWithReturn, httpPut,
    httpPutWithReturn
} from "@utils/rest.utils.ts";
import { Paging } from "@services/useGroupsService.ts";
import { RoleMappingSearchResults } from "@models/roleMappingSearchResults.model.ts";


const getArtifactTypes = async (config: ConfigService, auth: AuthService): Promise<ArtifactTypeInfo[]> => {
    console.info("[AdminService] Getting the global list of artifactTypes.");
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/config/artifactTypes");
    return httpGet<ArtifactTypeInfo[]>(endpoint, options);
};

const getRules = async (config: ConfigService, auth: AuthService): Promise<Rule[]> => {
    console.info("[AdminService] Getting the global list of rules.");
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/rules");
    return httpGet<string[]>(endpoint, options).then( ruleTypes => {
        return Promise.all(ruleTypes.map(rt => getRule(config, auth, rt)));
    });
};

const getRule = async (config: ConfigService, auth: AuthService, type: string): Promise<Rule> => {
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/rules/:rule", {
        rule: type
    });
    return httpGet<Rule>(endpoint, options);
};

const createRule = async (config: ConfigService, auth: AuthService, type: string, configValue: string): Promise<Rule> => {
    console.info("[AdminService] Creating global rule:", type);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/rules");
    const body: Rule = {
        config: configValue,
        type
    };
    return httpPostWithReturn(endpoint, body, options);
};

const updateRule = async (config: ConfigService, auth: AuthService, type: string, configValue: string): Promise<Rule|null> => {
    console.info("[AdminService] Updating global rule:", type);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/rules/:rule", {
        "rule": type
    });
    const body: Rule = { config: configValue, type };
    return httpPutWithReturn<Rule, Rule>(endpoint, body, options);
};

const deleteRule = async (config: ConfigService, auth: AuthService, type: string): Promise<null> => {
    console.info("[AdminService] Deleting global rule:", type);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/rules/:rule", {
        "rule": type
    });
    return httpDelete(endpoint, options);
};

const getRoleMappings = async (config: ConfigService, auth: AuthService, paging: Paging): Promise<RoleMappingSearchResults> => {
    console.info("[AdminService] Getting the list of role mappings.");
    const start: number = (paging.page - 1) * paging.pageSize;
    const end: number = start + paging.pageSize;
    const queryParams: any = {
        limit: end,
        offset: start
    };

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/roleMappings", {}, queryParams);
    return httpGet<RoleMappingSearchResults>(endpoint, options);
};

const getRoleMapping = async (config: ConfigService, auth: AuthService, principalId: string): Promise<RoleMapping> => {
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/roleMappings/:principalId", {
        principalId
    });
    return httpGet<RoleMapping>(endpoint, options);
};

const createRoleMapping = async (config: ConfigService, auth: AuthService, principalId: string, role: string, principalName: string): Promise<RoleMapping> => {
    console.info("[AdminService] Creating a role mapping:", principalId, role, principalName);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/roleMappings");
    const body: RoleMapping = { principalId, role, principalName };
    return httpPost(endpoint, body, options).then(() => {
        return body;
    });
};

const updateRoleMapping = async (config: ConfigService, auth: AuthService, principalId: string, role: string): Promise<RoleMapping> => {
    console.info("[AdminService] Updating role mapping:", principalId, role);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/roleMappings/:principalId", {
        principalId
    });
    const body: any = { role };
    return httpPut<any>(endpoint, body, options).then(() => {
        return { principalId, role, principalName: principalId };
    });
};

const deleteRoleMapping = async (config: ConfigService, auth: AuthService, principalId: string): Promise<null> => {
    console.info("[AdminService] Deleting role mapping for:", principalId);

    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/roleMappings/:principalId", {
        principalId
    });
    return httpDelete(endpoint, options);
};

const exportAs = async (config: ConfigService, auth: AuthService, filename: string): Promise<DownloadRef> => {
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    options.headers = {
        ...options.headers,
        "Accept": "application/zip"
    }

    const endpoint: string = createEndpoint(baseHref, "/admin/export", {}, {
        forBrowser: true
    });
    return httpGet<DownloadRef>(endpoint, options).then(ref => {
        if (ref.href.startsWith("/apis/registry/v2")) {
            ref.href = ref.href.replace("/apis/registry/v2", baseHref);
            ref.href = ref.href + "/" + filename;
        } else if (ref.href.startsWith("/apis/registry/v3")) {
            ref.href = ref.href.replace("/apis/registry/v3", baseHref);
            ref.href = ref.href + "/" + filename;
        }

        return ref;
    });
};

const importFrom = async (config: ConfigService, auth: AuthService, file: string | File, progressFunction: (progressEvent: any) => void): Promise<void> => {
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    options.headers = {
        ...options.headers,
        "Accept": "application/zip"
    }
    const endpoint: string = createEndpoint(baseHref, "/admin/import");
    return httpPost(endpoint, file, options,undefined, progressFunction);
};

const listConfigurationProperties = async (config: ConfigService, auth: AuthService): Promise<ConfigurationProperty[]> => {
    console.info("[AdminService] Getting the dynamic config properties.");
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/config/properties");
    return httpGet<ConfigurationProperty[]>(endpoint, options);
};

const setConfigurationProperty = async (config: ConfigService, auth: AuthService, propertyName: string, newValue: string): Promise<void> => {
    console.info("[AdminService] Setting a config property: ", propertyName);
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/config/properties/:propertyName", {
        propertyName
    });
    const body: UpdateConfigurationProperty = {
        value: newValue
    };
    return httpPut<UpdateConfigurationProperty>(endpoint, body, options);
};

const resetConfigurationProperty = async (config: ConfigService, auth: AuthService, propertyName: string): Promise<void> => {
    console.info("[AdminService] Resetting a config property: ", propertyName);
    const baseHref: string = config.artifactsUrl();
    const options = await createAuthOptions(auth);
    const endpoint: string = createEndpoint(baseHref, "/admin/config/properties/:propertyName", {
        propertyName
    });
    return httpDelete(endpoint, options);
};


export interface AdminService {
    getArtifactTypes(): Promise<ArtifactTypeInfo[]>;
    getRules(): Promise<Rule[]>;
    getRule(type: string): Promise<Rule>;
    createRule(type: string, config: string): Promise<Rule>;
    updateRule(type: string, config: string): Promise<Rule|null>;
    deleteRule(type: string): Promise<null>;
    getRoleMappings(paging: Paging): Promise<RoleMappingSearchResults>;
    getRoleMapping(principalId: string): Promise<RoleMapping>;
    createRoleMapping(principalId: string, role: string, principalName: string): Promise<RoleMapping>;
    updateRoleMapping(principalId: string, role: string): Promise<RoleMapping>;
    deleteRoleMapping(principalId: string): Promise<null>;
    exportAs(filename: string): Promise<DownloadRef>;
    importFrom(file: string | File, progressFunction: (progressEvent: any) => void): Promise<void>;
    listConfigurationProperties(): Promise<ConfigurationProperty[]>;
    setConfigurationProperty(propertyName: string, newValue: string): Promise<void>;
    resetConfigurationProperty(propertyName: string): Promise<void>;
}


export const useAdminService: () => AdminService = (): AdminService => {
    const config: ConfigService = useConfigService();
    const auth: AuthService = useAuth();

    return {
        getArtifactTypes(): Promise<ArtifactTypeInfo[]> {
            return getArtifactTypes(config, auth);
        },
        getRules(): Promise<Rule[]> {
            return getRules(config, auth);
        },
        getRule(type: string): Promise<Rule> {
            return getRule(config, auth, type);
        },
        createRule(type: string, configValue: string): Promise<Rule> {
            return createRule(config, auth, type, configValue);
        },
        updateRule(type: string, configValue: string): Promise<Rule|null> {
            return updateRule(config, auth, type, configValue);
        },
        deleteRule(type: string): Promise<null> {
            return deleteRule(config, auth, type);
        },
        getRoleMappings(paging: Paging): Promise<RoleMappingSearchResults> {
            return getRoleMappings(config, auth, paging);
        },
        getRoleMapping(principalId: string): Promise<RoleMapping> {
            return getRoleMapping(config, auth, principalId);
        },
        createRoleMapping(principalId: string, role: string, principalName: string): Promise<RoleMapping> {
            return createRoleMapping(config, auth, principalId, role, principalName);
        },
        updateRoleMapping(principalId: string, role: string): Promise<RoleMapping> {
            return updateRoleMapping(config, auth, principalId, role);
        },
        deleteRoleMapping(principalId: string): Promise<null> {
            return deleteRoleMapping(config, auth, principalId);
        },
        exportAs(filename: string): Promise<DownloadRef> {
            return exportAs(config, auth, filename);
        },
        importFrom(file: string | File, progressFunction: (progressEvent: any) => void): Promise<void> {
            return importFrom(config, auth, file, progressFunction);
        },
        listConfigurationProperties(): Promise<ConfigurationProperty[]> {
            return listConfigurationProperties(config, auth);
        },
        setConfigurationProperty(propertyName: string, newValue: string): Promise<void> {
            return setConfigurationProperty(config, auth, propertyName, newValue);
        },
        resetConfigurationProperty(propertyName: string): Promise<void> {
            return resetConfigurationProperty(config, auth, propertyName);
        }
    };
};
