/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {
    SearchedArtifact,
    SearchedVersion,
    ArtifactMetaData,
    Rule,
    VersionMetaData,
    ContentTypes
} from "@apicurio/registry-models";
import {BaseService} from "../baseService";
import YAML from "yaml";
import {LoggerService} from "../logger";
import {ArtifactsService} from "../artifacts";

export interface CreateArtifactData {
    groupId: string;
    id: string|null;
    type: string;
    content: string;
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

export interface ArtifactsSearchResults {
    artifacts: SearchedArtifact[];
    count: number;
    page: number;
    pageSize: number;
}

export interface EditableMetaData {
    name: string;
    description: string;
    labels: string[];
}

/**
 * The artifacts service.  Used to query the backend search API to fetch lists of
 * artifacts and also details about individual artifacts.
 */
export class GroupsService extends BaseService {

    protected artifacts: ArtifactsService = null;

    public createArtifact(data: CreateArtifactData): Promise<ArtifactMetaData> {
        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts", { groupId: data.groupId });
        const headers: any = {};
        if (data.id) {
            headers["X-Registry-ArtifactId"] = data.id;
        }
        if (data.type) {
            headers["X-Registry-ArtifactType"] = data.type;
        }
        headers["Content-Type"] = this.contentType(data.type, data.content);
        return this.httpPostWithReturn<any, ArtifactMetaData>(endpoint, data.content, this.options(headers));
    }

    public createArtifactVersion(groupId: string|null, artifactId: string, data: CreateVersionData): Promise<VersionMetaData> {
        if (groupId == null) {
            return this.artifacts.createArtifactVersion(artifactId, data);
        }

        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/versions", { groupId, artifactId });
        const headers: any = {};
        if (data.type) {
            headers["X-Registry-ArtifactType"] = data.type;
        }
        headers["Content-Type"] = this.contentType(data.type, data.content);
        return this.httpPostWithReturn<any, VersionMetaData>(endpoint, data.content, this.options(headers));
    }

    public getArtifacts(criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactsSearchResults> {
        this.logger.debug("[GroupsService] Getting artifacts: ", criteria, paging);
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
        const endpoint: string = this.endpoint("/v2/search/artifacts", {}, queryParams);
        return this.httpGet<ArtifactsSearchResults>(endpoint, undefined, (data) => {
            const results: ArtifactsSearchResults = {
                artifacts: data.artifacts,
                count: data.count,
                page: paging.page,
                pageSize: paging.pageSize
            };
            return results;
        });
    }

    public getArtifactMetaData(groupId: string|null, artifactId: string, version: string): Promise<ArtifactMetaData> {
        if (groupId == null) {
            return this.artifacts.getArtifactMetaData(artifactId, version);
        }

        let endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/versions/:version/meta", { groupId, artifactId, version });
        if (version === "latest") {
            endpoint = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/meta", { groupId, artifactId });
        }
        return this.httpGet<ArtifactMetaData>(endpoint);
    }

    public updateArtifactMetaData(groupId: string|null, artifactId: string, version: string, metaData: EditableMetaData): Promise<void> {
        if (groupId == null) {
            return this.artifacts.updateArtifactMetaData(artifactId, version,  metaData);
        }

        let endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/versions/:version/meta", { groupId, artifactId, version });
        if (version === "latest") {
            endpoint = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/meta", { groupId, artifactId });
        }
        return this.httpPut<EditableMetaData>(endpoint, metaData);
    }

    public getArtifactContent(groupId: string|null, artifactId: string, version: string): Promise<string> {
        if (groupId == null) {
            return this.artifacts.getArtifactContent(artifactId, version);
        }

        let endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/versions/:version", { groupId, artifactId, version });
        if (version === "latest") {
            endpoint = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
        }

        const options: any = this.options({
            "Accept": "*"
        });
        options.maxContentLength = "‭5242880‬"; // TODO 5MB hard-coded, make this configurable?
        options.responseType = "text";
        options.transformResponse = (data: any) => data;
        return this.httpGet<string>(endpoint, options);
    }

    public getArtifactVersions(groupId: string|null, artifactId: string): Promise<SearchedVersion[]> {
        if (groupId == null) {
            return this.artifacts.getArtifactVersions(artifactId);
        }

        this.logger.info("[GroupsService] Getting the list of versions for artifact: ", groupId, artifactId);
        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/versions", { groupId, artifactId }, {
            limit: 500,
            offset: 0
        });
        return this.httpGet<SearchedVersion[]>(endpoint, undefined, (data) => {
            return data.versions;
        });
    }

    public getArtifactRules(groupId: string|null, artifactId: string): Promise<Rule[]> {
        if (groupId == null) {
            return this.artifacts.getArtifactRules(artifactId);
        }

        this.logger.info("[GroupsService] Getting the list of rules for artifact: ", groupId, artifactId);
        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/rules", { groupId, artifactId });
        return this.httpGet<string[]>(endpoint).then( ruleTypes => {
            return Promise.all(ruleTypes.map(rt => this.getArtifactRule(groupId, artifactId, rt)));
        });
    }

    public getArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<Rule> {
        if (groupId == null) {
            return this.artifacts.getArtifactRule(artifactId, type);
        }

        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/rules/:rule", {
            groupId,
            artifactId,
            rule: type
        });
        return this.httpGet<Rule>(endpoint);
    }

    public createArtifactRule(groupId: string|null, artifactId: string, type: string, config: string): Promise<Rule> {
        if (groupId == null) {
            return this.artifacts.createArtifactRule(artifactId, type, config);
        }

        this.logger.info("[GroupsService] Creating rule:", type);

        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/rules", { groupId, artifactId });
        const body: Rule = {
            config,
            type
        };
        return this.httpPostWithReturn(endpoint, body);
    }

    public updateArtifactRule(groupId: string|null, artifactId: string, type: string, config: string): Promise<Rule> {
        if (groupId == null) {
            return this.artifacts.updateArtifactRule(artifactId, type, config);
        }

        this.logger.info("[GroupsService] Updating rule:", type);
        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/rules/:rule", {
            groupId,
            artifactId,
            "rule": type
        });
        const body: Rule = { config, type };
        return this.httpPutWithReturn<Rule, Rule>(endpoint, body);
    }

    public deleteArtifactRule(groupId: string|null, artifactId: string, type: string): Promise<void> {
        if (groupId == null) {
            return this.artifacts.deleteArtifactRule(artifactId, type);
        }

        this.logger.info("[GroupsService] Deleting rule:", type);
        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId/rules/:rule", {
            groupId,
            artifactId,
            "rule": type
        });
        return this.httpDelete(endpoint);
    }

    public deleteArtifact(groupId: string|null, artifactId: string): Promise<void> {
        if (groupId == null) {
            return this.artifacts.deleteArtifact(artifactId);
        }

        this.logger.info("[GroupsService] Deleting artifact:", groupId, artifactId);
        const endpoint: string = this.endpoint("/v2/groups/:groupId/artifacts/:artifactId", { groupId, artifactId });
        return this.httpDelete(endpoint);
    }

    private contentType(type: string, content: string): string {
        switch (type) {
            case "PROTOBUF":
                return ContentTypes.APPLICATION_PROTOBUF;
            case "WSDL":
            case "XSD":
            case "XML":
                return ContentTypes.APPLICATION_XML;
            case "GRAPHQL":
                return ContentTypes.APPLICATION_GRAPHQL;
        }
        if (this.isJson(content)) {
            return ContentTypes.APPLICATION_JSON;
        } else if (this.isXml(content)) {
            return ContentTypes.APPLICATION_XML;
        } else if (this.isYaml(content)) {
            return ContentTypes.APPLICATION_YAML;
        } else {
            return "application/octet-stream";
        }
    }

    private isJson(content: string): boolean {
        try {
            JSON.parse(content);
            return true;
        } catch (e) {
            return false;
        }
    }

    private isXml(content: string): boolean {
        try {
            const xmlParser: DOMParser = new DOMParser();
            const dom: Document = xmlParser.parseFromString(content, "application/xml");
            const isParseError: boolean = dom.documentElement.nodeName === "parsererror";
            return !isParseError;
        } catch (e) {
            return false;
        }
    }

    private isYaml(content: string): boolean {
        try {
            const parsedContent: any = YAML.parse(content);
            return typeof parsedContent === "object";
        } catch (e) {
            return false;
        }
    }
}
