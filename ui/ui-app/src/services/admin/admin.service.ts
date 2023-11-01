import { BaseService } from "../baseService";
import { ConfigurationProperty } from "@models/configurationProperty.model";
import { UpdateConfigurationProperty } from "@models/updateConfigurationProperty.model";
import { ArtifactTypeInfo } from "@models/artifactTypeInfo.model";
import { Rule } from "@models/rule.model";
import { RoleMapping } from "@models/roleMapping.model";
import { DownloadRef } from "@models/downloadRef.model";

/**
 * The Admin service.  Used to get global/settings information from the back-end, like global
 * rules and logging settings.
 */
export class AdminService extends BaseService {

    public getArtifactTypes(): Promise<ArtifactTypeInfo[]> {
        this.logger?.info("[AdminService] Getting the global list of artifactTypes.");
        const endpoint: string = this.endpoint("/admin/artifactTypes");
        return this.httpGet<ArtifactTypeInfo[]>(endpoint);
    }

    public getRules(): Promise<Rule[]> {
        this.logger?.info("[AdminService] Getting the global list of rules.");
        const endpoint: string = this.endpoint("/admin/rules");
        return this.httpGet<string[]>(endpoint).then( ruleTypes => {
            return Promise.all(ruleTypes.map(rt => this.getRule(rt)));
        });
    }

    public getRule(type: string): Promise<Rule> {
        const endpoint: string = this.endpoint("/admin/rules/:rule", {
            rule: type
        });
        return this.httpGet<Rule>(endpoint);
    }

    public createRule(type: string, config: string): Promise<Rule> {
        this.logger?.info("[AdminService] Creating global rule:", type);

        const endpoint: string = this.endpoint("/admin/rules");
        const body: Rule = {
            config,
            type
        };
        return this.httpPostWithReturn(endpoint, body);
    }

    public updateRule(type: string, config: string): Promise<Rule|null> {
        this.logger?.info("[AdminService] Updating global rule:", type);

        const endpoint: string = this.endpoint("/admin/rules/:rule", {
            "rule": type
        });
        const body: Rule = { config, type };
        return this.httpPutWithReturn<Rule, Rule>(endpoint, body);
    }

    public deleteRule(type: string): Promise<null> {
        this.logger?.info("[AdminService] Deleting global rule:", type);

        const endpoint: string = this.endpoint("/admin/rules/:rule", {
            "rule": type
        });
        return this.httpDelete(endpoint);
    }

    public getRoleMappings(): Promise<RoleMapping[]> {
        this.logger?.info("[AdminService] Getting the list of role mappings.");
        const endpoint: string = this.endpoint("/admin/roleMappings");
        return this.httpGet<RoleMapping[]>(endpoint);
    }

    public getRoleMapping(principalId: string): Promise<RoleMapping> {
        const endpoint: string = this.endpoint("/admin/roleMappings/:principalId", {
            principalId
        });
        return this.httpGet<RoleMapping>(endpoint);
    }

    public createRoleMapping(principalId: string, role: string, principalName: string): Promise<RoleMapping> {
        this.logger?.info("[AdminService] Creating a role mapping:", principalId, role, principalName);

        const endpoint: string = this.endpoint("/admin/roleMappings");
        const body: RoleMapping = { principalId, role, principalName };
        return this.httpPost(endpoint, body).then(() => {
            return body;
        });
    }

    public updateRoleMapping(principalId: string, role: string): Promise<RoleMapping> {
        this.logger?.info("[AdminService] Updating role mapping:", principalId, role);

        const endpoint: string = this.endpoint("/admin/roleMappings/:principalId", {
            principalId
        });
        const body: any = { role };
        return this.httpPut<any>(endpoint, body).then(() => {
            return { principalId, role, principalName: principalId };
        });
    }

    public deleteRoleMapping(principalId: string): Promise<null> {
        this.logger?.info("[AdminService] Deleting role mapping for:", principalId);

        const endpoint: string = this.endpoint("/admin/roleMappings/:principalId", {
            principalId
        });
        return this.httpDelete(endpoint);
    }

    public exportAs(filename: string): Promise<DownloadRef> {
        const endpoint: string = this.endpoint("/admin/export", {}, {
            forBrowser: true
        });
        const headers: any = {
            Accept: "application/zip"
        };
        return this.httpGet<DownloadRef>(endpoint, this.options(headers)).then(ref => {
            if (ref.href.startsWith("/apis/registry/v2")) {
                ref.href = ref.href.replace("/apis/registry/v2", this.apiBaseHref());
                ref.href = ref.href + "/" + filename;
            }

            return ref;
        });
    }

    public importFrom(file: string | File, progressFunction: (progressEvent: any) => void): Promise<void> {
        const endpoint: string = this.endpoint("/admin/import");
        const headers: any = {
            "Content-Type": "application/zip"
        };
        return this.httpPost(endpoint, file, this.options(headers),undefined, progressFunction);
    }

    public listConfigurationProperties(): Promise<ConfigurationProperty[]> {
        this.logger?.info("[AdminService] Getting the dynamic config properties.");
        const endpoint: string = this.endpoint("/admin/config/properties");
        return this.httpGet<ConfigurationProperty[]>(endpoint);
    }

    public setConfigurationProperty(propertyName: string, newValue: string): Promise<void> {
        this.logger?.info("[AdminService] Setting a config property: ", propertyName);
        const endpoint: string = this.endpoint("/admin/config/properties/:propertyName", {
            propertyName
        });
        const body: UpdateConfigurationProperty = {
            value: newValue
        };
        return this.httpPut<UpdateConfigurationProperty>(endpoint, body);
    }

    public resetConfigurationProperty(propertyName: string): Promise<void> {
        this.logger?.info("[AdminService] Resetting a config property: ", propertyName);
        const endpoint: string = this.endpoint("/admin/config/properties/:propertyName", {
            propertyName
        });
        return this.httpDelete(endpoint);
    }

}
