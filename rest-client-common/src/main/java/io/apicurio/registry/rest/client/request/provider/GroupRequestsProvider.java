/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest.client.request.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.request.ErrorHandler;
import io.apicurio.registry.rest.client.request.Request;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.request.provider.Operation.DELETE;
import static io.apicurio.registry.rest.client.request.provider.Operation.GET;
import static io.apicurio.registry.rest.client.request.provider.Operation.POST;
import static io.apicurio.registry.rest.client.request.provider.Operation.PUT;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_METADATA;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_RULE;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_RULES;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_STATE;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_TEST;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_VERSION;
import static io.apicurio.registry.rest.client.request.provider.Routes.ARTIFACT_VERSIONS;
import static io.apicurio.registry.rest.client.request.provider.Routes.GROUP_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.VERSION_METADATA;
import static io.apicurio.registry.rest.client.request.provider.Routes.VERSION_STATE;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class GroupRequestsProvider {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Request<Void> deleteArtifactsInGroup(String groupId) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(GROUP_BASE_PATH)
                .pathParams(List.of(groupId))
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<ArtifactMetaData> createArtifact(String groupId, Map<String, String> headers, InputStream data, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(POST)
                .path(GROUP_BASE_PATH)
                .headers(headers)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<ArtifactMetaData>(){})
                .data(data)
                .build();
    }

    public static Request<ArtifactSearchResults> listArtifactsInGroup(String groupId, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(GET)
                .path(GROUP_BASE_PATH)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<ArtifactSearchResults>(){})
                .build();
    }

    public static Request<VersionMetaData> createArtifactVersion(String groupId, String artifactId, InputStream data, Map<String, String> headers) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(POST)
                .path(ARTIFACT_VERSIONS)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<VersionMetaData>(){})
                .data(data)
                .build();
    }

    public static Request<VersionSearchResults> listArtifactVersions(String groupId, String artifactId, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<VersionSearchResults>()
                .operation(GET)
                .path(ARTIFACT_VERSIONS)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseType(new TypeReference<VersionSearchResults>(){})
                .build();
    }

    public static Request<Void> updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) {
        try {
            return new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(VERSION_STATE)
                    .pathParams(List.of(groupId, artifactId, version))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseType(new TypeReference<Void>(){})
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<Void> deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<Void> updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) {
        try {
            return new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(VERSION_METADATA)
                    .pathParams(List.of(groupId, artifactId, version))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseType(new TypeReference<Void>(){})
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<VersionMetaData> getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(GET)
                .path(VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<VersionMetaData>(){})
                .build();
    }

    public static Request<InputStream> getArtifactVersion(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(ARTIFACT_VERSION)
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<InputStream>(){})
                .build();
    }

    public static Request<Void> testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        return new Request.RequestBuilder<Void>()
                .operation(PUT)
                .path(ARTIFACT_TEST)
                .pathParams(List.of(groupId, artifactId))
                .data(data)
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<Void> updateArtifactState(String groupId, String artifactId, UpdateState data) {
        try {
            return new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(ARTIFACT_STATE)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseType(new TypeReference<Void>(){})
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<Void> deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ARTIFACT_RULE)
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<Rule> updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        try {
            return new Request.RequestBuilder<Rule>()
                    .operation(PUT)
                    .responseType(new TypeReference<Rule>(){})
                    .path(ARTIFACT_RULE)
                    .pathParams(List.of(groupId, artifactId, rule.value()))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<Rule> getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return new Request.RequestBuilder<Rule>()
                .operation(GET)
                .path(ARTIFACT_RULE)
                .responseType(new TypeReference<Rule>(){})
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .build();
    }

    public static Request<Void> deleteArtifactRules(String groupId, String artifactId) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<Void> createArtifactRule(String groupId, String artifactId, Rule data) {
        try {
            return new Request.RequestBuilder<Void>()
                    .operation(POST)
                    .path(ARTIFACT_RULES)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseType(new TypeReference<Void>(){})
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<List<RuleType>> listArtifactRules(String groupId, String artifactId) {
        return new Request.RequestBuilder<List<RuleType>>()
                .operation(GET)
                .path(ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<List<RuleType>>(){})
                .build();
    }

    public static Request<VersionMetaData> getArtifactVersionMetaDataByContent(String groupId, String artifactId, Map<String, List<String>> queryParams, InputStream data) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(POST)
                .path(ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseType(new TypeReference<VersionMetaData>(){})
                .data(data)
                .build();
    }

    public static Request<Void> updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        try {
            return new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(ARTIFACT_METADATA)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseType(new TypeReference<Void>(){})
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<ArtifactMetaData> getArtifactMetaData(String groupId, String artifactId) {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(GET)
                .path(ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<ArtifactMetaData>(){})
                .build();
    }

    public static Request<Void> deleteArtifact(String groupId, String artifactId) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ARTIFACT_BASE_PATH)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<ArtifactMetaData> updateArtifact(String groupId, String artifactId, InputStream data) {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(PUT)
                .path(ARTIFACT_BASE_PATH)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<ArtifactMetaData>(){})
                .data(data)
                .build();
    }

    public static Request<InputStream> getLatestArtifact(String groupId, String artifactId) {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(ARTIFACT_BASE_PATH)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<InputStream>(){})
                .build();
    }
}
