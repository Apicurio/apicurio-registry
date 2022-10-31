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
import io.apicurio.registry.rest.v2.beans.*;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.rest.client.request.Operation;
import io.apicurio.rest.client.request.Request;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.apicurio.rest.client.request.Operation.GET;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class GroupRequestsProvider {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Request<Void> deleteArtifactsInGroup(String groupId) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.DELETE)
                .path(Routes.ARTIFACT_GROUPS_BASE_PATH)
                .pathParams(List.of(groupId))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<ArtifactMetaData> createArtifact(String groupId, Map<String, String> headers, InputStream data, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(Operation.POST)
                .path(Routes.ARTIFACT_GROUPS_BASE_PATH)
                .headers(headers)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<ArtifactMetaData>() {
                })
                .data(data)
                .build();
    }

    public static Request<ArtifactMetaData> createArtifactWithReferences(String groupId, Map<String, String> headers, ContentCreateRequest data, Map<String, List<String>> queryParams)
            throws JsonProcessingException {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(Operation.POST)
                .path(Routes.ARTIFACT_GROUPS_BASE_PATH)
                .headers(headers)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<ArtifactMetaData>() {
                })
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .build();
    }

    public static Request<ArtifactSearchResults> listArtifactsInGroup(String groupId, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_GROUPS_BASE_PATH)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<ArtifactSearchResults>() {
                })
                .build();
    }

    public static Request<VersionMetaData> createArtifactVersion(String groupId, String artifactId, InputStream data, Map<String, String> headers) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(Operation.POST)
                .path(Routes.ARTIFACT_VERSIONS)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<VersionMetaData>() {
                })
                .data(data)
                .build();
    }

    public static Request<VersionSearchResults> listArtifactVersions(String groupId, String artifactId, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<VersionSearchResults>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_VERSIONS)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseType(new TypeReference<VersionSearchResults>() {
                })
                .build();
    }

    public static Request<Void> updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.VERSION_STATE)
                .pathParams(List.of(groupId, artifactId, version))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.DELETE)
                .path(Routes.VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<VersionMetaData> getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(Operation.GET)
                .path(Routes.VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<VersionMetaData>() {
                })
                .build();
    }

    public static Request<InputStream> getArtifactVersion(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<InputStream>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_VERSION)
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<InputStream>() {
                })
                .build();
    }

    public static Request<Void> testUpdateArtifact(String groupId, String artifactId, Map<String, String> headers, InputStream data) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_TEST)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .data(data)
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_TEST)
                .pathParams(List.of(groupId, artifactId))
                .data(data)
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> updateArtifactState(String groupId, String artifactId, UpdateState data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_STATE)
                .pathParams(List.of(groupId, artifactId))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.DELETE)
                .path(Routes.ARTIFACT_RULE)
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Rule> updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) throws JsonProcessingException {
        return new Request.RequestBuilder<Rule>()
                .operation(Operation.PUT)
                .responseType(new TypeReference<Rule>() {
                })
                .path(Routes.ARTIFACT_RULE)
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .build();
    }

    public static Request<Rule> getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return new Request.RequestBuilder<Rule>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_RULE)
                .responseType(new TypeReference<Rule>() {
                })
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .build();
    }

    public static Request<Void> deleteArtifactRules(String groupId, String artifactId) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.DELETE)
                .path(Routes.ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> createArtifactRule(String groupId, String artifactId, Rule data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.POST)
                .path(Routes.ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<List<RuleType>> listArtifactRules(String groupId, String artifactId) {
        return new Request.RequestBuilder<List<RuleType>>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<List<RuleType>>() {
                })
                .build();
    }

    public static Request<VersionMetaData> getArtifactVersionMetaDataByContent(String groupId, String artifactId, Map<String, String> headers, Map<String, List<String>> queryParams, InputStream data) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(Operation.POST)
                .path(Routes.ARTIFACT_METADATA)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseType(new TypeReference<VersionMetaData>() {
                })
                .data(data)
                .build();
    }

    public static Request<VersionMetaData> getArtifactVersionMetaDataByContent(String groupId, String artifactId, Map<String, List<String>> queryParams, InputStream data) {
        return new Request.RequestBuilder<VersionMetaData>()
                .operation(Operation.POST)
                .path(Routes.ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseType(new TypeReference<VersionMetaData>() {
                })
                .data(data)
                .build();
    }


    public static Request<Void> updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> updateArtifactOwner(String groupId, String artifactId, ArtifactOwner owner) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_OWNER)
                .pathParams(List.of(groupId, artifactId))
                .data(IoUtil.toStream(mapper.writeValueAsBytes(owner)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<ArtifactMetaData> getArtifactMetaData(String groupId, String artifactId) {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<ArtifactMetaData>() {
                })
                .build();
    }

    public static Request<ArtifactOwner> getArtifactOwner(String groupId, String artifactId) {
        return new Request.RequestBuilder<ArtifactOwner>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_OWNER)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<ArtifactOwner>() {
                })
                .build();
    }

    public static Request<Void> deleteArtifact(String groupId, String artifactId) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.DELETE)
                .path(Routes.ARTIFACT_BASE_PATH)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<ArtifactMetaData> updateArtifact(String groupId, String artifactId, Map<String, String> headers, InputStream data) {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_BASE_PATH)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<ArtifactMetaData>() {
                })
                .data(data)
                .build();
    }

    public static Request<ArtifactMetaData> updateArtifactWithReferences(String groupId, String artifactId, Map<String, String> headers, ContentCreateRequest data) throws JsonProcessingException {
        return new Request.RequestBuilder<ArtifactMetaData>()
                .operation(Operation.PUT)
                .path(Routes.ARTIFACT_BASE_PATH)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<ArtifactMetaData>() {
                })
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .build();
    }


    public static Request<InputStream> getLatestArtifact(String groupId, String artifactId) {
        return new Request.RequestBuilder<InputStream>()
                .operation(Operation.GET)
                .path(Routes.ARTIFACT_BASE_PATH)
                .pathParams(List.of(groupId, artifactId))
                .responseType(new TypeReference<InputStream>() {
                })
                .build();
    }

    public static Request<List<ArtifactReference>> getArtifactReferencesByCoordinates(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<List<ArtifactReference>>()
                .operation(GET)
                .path(Routes.ARTIFACT_VERSION_REFERENCES)
                .pathParams(List.of(groupId == null ? "null" : groupId, artifactId, version))
                .responseType(new TypeReference<List<ArtifactReference>>() {
                })
                .build();
    }

    public static Request<Void> createArtifactGroup(GroupMetaData groupMetaData) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.POST)
                .path(Routes.GROUPS_BASE_PATH)
                .data(IoUtil.toStream(mapper.writeValueAsBytes(groupMetaData)))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> deleteArtifactGroup(String groupId) {
        return new Request.RequestBuilder<Void>()
                .operation(Operation.DELETE)
                .path(Routes.GROUP_BASE_PATH)
                .pathParams(List.of(groupId))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<GroupMetaData> getArtifactGroup(String groupId) {
        return new Request.RequestBuilder<GroupMetaData>()
                .operation(GET)
                .path(Routes.GROUP_BASE_PATH)
                .pathParams(List.of(groupId))
                .responseType(new TypeReference<GroupMetaData>() {
                })
                .build();
    }

    public static Request<GroupSearchResults> listGroups(Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<GroupSearchResults>()
                .operation(Operation.GET)
                .path(Routes.GROUPS_BASE_PATH)
                .queryParams(queryParams)
                .responseType(new TypeReference<GroupSearchResults>() {
                })
                .build();
    }
}
