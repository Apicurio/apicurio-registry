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
    SearchedVersion,
    ArtifactMetaData,
    Rule,
    VersionMetaData,
    ContentTypes
} from "@apicurio/registry-models";
import {BaseService} from "../baseService";
import YAML from "yaml";
import {
    ArtifactsSearchResults,
    CreateArtifactData,
    CreateVersionData,
    EditableMetaData,
    GetArtifactsCriteria,
    Paging
} from "../groups";

/**
 * The artifacts service.  Used to query the backend search API to fetch lists of
 * artifacts and also details about individual artifacts.
 */
export class ArtifactsService extends BaseService {

    public createArtifact(data: CreateArtifactData): Promise<ArtifactMetaData> {
        const endpoint: string = this.endpoint("/v1/artifacts");
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

    public createArtifactVersion(artifactId: string, data: CreateVersionData): Promise<VersionMetaData> {
        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId/versions", { artifactId });
        const headers: any = {};
        if (data.type) {
            headers["X-Registry-ArtifactType"] = data.type;
        }
        headers["Content-Type"] = this.contentType(data.type, data.content);
        return this.httpPostWithReturn<any, VersionMetaData>(endpoint, data.content, this.options(headers));
    }

    public getArtifacts(criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactsSearchResults> {
        this.logger.debug("[ArtifactsService] Getting artifacts: ", criteria, paging);
        const start: number = (paging.page - 1) * paging.pageSize;
        const end: number = start + paging.pageSize;
        const endpoint: string = this.endpoint("/v1/search/artifacts", {}, {
            limit: end,
            offset: start,
            order: criteria.sortAscending ? "asc" : "desc",
            over: criteria.type,
            search: criteria.value
        });
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

    public getArtifactMetaData(artifactId: string, version: string): Promise<ArtifactMetaData> {
        let endpoint: string = this.endpoint("/v1/artifacts/:artifactId/versions/:version/meta", { artifactId, version });
        if (version === "latest") {
            endpoint = this.endpoint("/v1/artifacts/:artifactId/meta", { artifactId });
        }
        return this.httpGet<ArtifactMetaData>(endpoint);
    }

    public updateArtifactMetaData(artifactId: string, version: string, metaData: EditableMetaData): Promise<void> {
        let endpoint: string = this.endpoint("/v1/artifacts/:artifactId/versions/:version/meta", { artifactId, version });
        if (version === "latest") {
            endpoint = this.endpoint("/v1/artifacts/:artifactId/meta", { artifactId });
        }
        return this.httpPut<EditableMetaData>(endpoint, metaData);
    }

    public getArtifactContent(artifactId: string, version: string): Promise<string> {
        let endpoint: string = this.endpoint("/v1/artifacts/:artifactId/versions/:version", { artifactId, version });
        if (version === "latest") {
            endpoint = this.endpoint("/v1/artifacts/:artifactId", { artifactId });
        }

        const options: any = this.options({
            "Accept": "*"
        });
        options.maxContentLength = "‭5242880‬"; // TODO 5MB hard-coded, make this configurable?
        options.responseType = "text";
        options.transformResponse = (data: any) => data;
        return this.httpGet<string>(endpoint, options);
    }

    public getArtifactVersions(artifactId: string): Promise<SearchedVersion[]> {
        this.logger.info("[ArtifactsService] Getting the list of versions for artifact: ", artifactId);
        const endpoint: string = this.endpoint("/v1/search/artifacts/:artifactId/versions", { artifactId }, {
            limit: 500,
            offset: 0
        });
        return this.httpGet<SearchedVersion[]>(endpoint, undefined, (data) => {
            return data.versions;
        });
    }

    public getArtifactRules(artifactId: string): Promise<Rule[]> {
        this.logger.info("[ArtifactsService] Getting the list of rules for artifact: ", artifactId);
        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId/rules", { artifactId });
        return this.httpGet<string[]>(endpoint).then( ruleTypes => {
            return Promise.all(ruleTypes.map(rt => this.getArtifactRule(artifactId, rt)));
        });
    }

    public getArtifactRule(artifactId: string, type: string): Promise<Rule> {
        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId/rules/:rule", {
            artifactId,
            rule: type
        });
        return this.httpGet<Rule>(endpoint);
    }

    public createArtifactRule(artifactId: string, type: string, config: string): Promise<Rule> {
        this.logger.info("[ArtifactsService] Creating rule:", type);

        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId/rules", { artifactId });
        const body: Rule = {
            config,
            type
        };
        return this.httpPostWithReturn(endpoint, body);
    }

    public updateArtifactRule(artifactId: string, type: string, config: string): Promise<Rule> {
        this.logger.info("[ArtifactsService] Updating rule:", type);
        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId/rules/:rule", {
            artifactId,
            "rule": type
        });
        const body: Rule = { config, type };
        return this.httpPutWithReturn<Rule, Rule>(endpoint, body);
    }

    public deleteArtifactRule(artifactId: string, type: string): Promise<void> {
        this.logger.info("[ArtifactsService] Deleting rule:", type);
        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId/rules/:rule", {
            artifactId,
            "rule": type
        });
        return this.httpDelete(endpoint);
    }

    public deleteArtifact(artifactId: string): Promise<void> {
        this.logger.info("[ArtifactsService] Deleting artifact:", artifactId);
        const endpoint: string = this.endpoint("/v1/artifacts/:artifactId", { artifactId });
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
