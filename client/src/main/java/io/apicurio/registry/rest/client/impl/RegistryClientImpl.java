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

package io.apicurio.registry.rest.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.request.Request;
import io.apicurio.registry.rest.client.request.RequestHandler;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_BASE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_METADATA;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_RULE;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_RULES;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_STATE;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_TEST;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_VERSION;
import static io.apicurio.registry.rest.client.impl.Routes.ARTIFACT_VERSIONS;
import static io.apicurio.registry.rest.client.impl.Routes.GROUP_BASE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.IDS_CONTENT_HASH;
import static io.apicurio.registry.rest.client.impl.Routes.IDS_CONTENT_ID;
import static io.apicurio.registry.rest.client.impl.Routes.IDS_GLOBAL_ID;
import static io.apicurio.registry.rest.client.impl.Routes.LOGS_BASE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.LOG_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.RULES_BASE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.RULE_PATH;
import static io.apicurio.registry.rest.client.impl.Routes.SEARCH_ARTIFACTS;
import static io.apicurio.registry.rest.client.impl.Routes.VERSION_METADATA;
import static io.apicurio.registry.rest.client.impl.Routes.VERSION_STATE;
import static io.apicurio.registry.rest.client.request.Request.Operation.DELETE;
import static io.apicurio.registry.rest.client.request.Request.Operation.GET;
import static io.apicurio.registry.rest.client.request.Request.Operation.POST;
import static io.apicurio.registry.rest.client.request.Request.Operation.PUT;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryClientImpl implements RegistryClient {

    private final RequestHandler requestHandler;
    private final ObjectMapper mapper;

    public RegistryClientImpl(String endpoint) {
        this(endpoint, null);
    }

    public RegistryClientImpl(String endpoint, Auth auth) {
        requestHandler = new RequestHandler(endpoint, auth);
        mapper = new ObjectMapper();
    }

    @Override
    public InputStream getLatestArtifact(String groupId, String artifactId) {
        return requestHandler
                .sendRequest(new Request.RequestBuilder<InputStream>()
                        .operation(GET)
                        .path(ARTIFACT_BASE_PATH)
                        .pathParams(List.of(groupId, artifactId))
                        .responseClass(InputStream.class)
                        .build());
    }

    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream data) {
        return requestHandler
                .sendRequest(new Request.RequestBuilder<ArtifactMetaData>()
                        .operation(PUT)
                        .path(ARTIFACT_BASE_PATH)
                        .pathParams(List.of(groupId, artifactId))
                        .responseClass(ArtifactMetaData.class)
                        .data(data)
                        .build());
    }

    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ARTIFACT_BASE_PATH)
                .pathParams(List.of(groupId, artifactId))
                .responseClass(Void.class)
                .build());
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        return requestHandler.sendRequest(new Request.RequestBuilder<ArtifactMetaData>()
                .operation(GET)
                .path(ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .responseClass(ArtifactMetaData.class)
                .build());
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(ARTIFACT_METADATA)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());

        } catch (IOException e) {
            throw parseError(e);
        }
    }

    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data) {
        final Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();
        return requestHandler.sendRequest(new Request.RequestBuilder<VersionMetaData>()
                .operation(POST)
                .path(ARTIFACT_METADATA)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseClass(VersionMetaData.class)
                .data(data)
                .build());
    }

    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {

        return requestHandler.sendRequest(new Request.RequestBuilder<List>()
                .operation(GET)
                .path(ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .responseClass(List.class)
                .build());
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(POST)
                    .path(ARTIFACT_RULES)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ARTIFACT_RULES)
                .pathParams(List.of(groupId, artifactId))
                .responseClass(Void.class)
                .build());
    }

    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return requestHandler.sendRequest(new Request.RequestBuilder<Rule>()
                .operation(GET)
                .path(ARTIFACT_RULE)
                .responseClass(Rule.class)
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .build());
    }

    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule,
                                         Rule data) {
        try {
            return requestHandler.sendRequest(new Request.RequestBuilder<Rule>()
                    .operation(PUT)
                    .responseClass(Rule.class)
                    .path(ARTIFACT_RULE)
                    .pathParams(List.of(groupId, artifactId, rule.value()))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .build());

        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ARTIFACT_RULE)
                .pathParams(List.of(groupId, artifactId, rule.value()))
                .responseClass(Void.class)
                .build());
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(ARTIFACT_STATE)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(ARTIFACT_TEST)
                    .pathParams(List.of(groupId, artifactId))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
        return requestHandler.sendRequest(new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(ARTIFACT_VERSION)
                .pathParams(List.of(groupId, artifactId, version))
                .responseClass(InputStream.class)
                .build());
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return requestHandler.sendRequest(new Request.RequestBuilder<VersionMetaData>()
                .operation(GET)
                .path(VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .responseClass(VersionMetaData.class)
                .build());
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData
            data) {
        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(VERSION_METADATA)
                    .pathParams(List.of(groupId, artifactId, version))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(VERSION_METADATA)
                .pathParams(List.of(groupId, artifactId, version))
                .responseClass(Void.class)
                .build());
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
                                           UpdateState data) {
        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(PUT)
                    .path(VERSION_STATE)
                    .pathParams(List.of(groupId, artifactId, version))
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset, Integer
            limit) {

        Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(null, null, limit, offset, queryParams);

        return requestHandler.sendRequest(new Request.RequestBuilder<VersionSearchResults>()
                .operation(GET)
                .path(ARTIFACT_VERSIONS)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseClass(VersionSearchResults.class)
                .build());
    }

    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String version, InputStream
            data) {

        final Map<String, String> headers = version != null ? Map.of(Headers.VERSION, version) : Collections.emptyMap();
        return requestHandler.sendRequest(new Request.RequestBuilder<VersionMetaData>()
                .operation(POST)
                .path(ARTIFACT_VERSIONS)
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .responseClass(VersionMetaData.class)
                .data(data)
                .build());
    }

    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order, Integer
            limit, Integer offset) {

        final Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);

        return requestHandler.sendRequest(new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(GET)
                .path(GROUP_BASE_PATH)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseClass(ArtifactSearchResults.class)
                .build());
    }

    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, IfExists ifExists, Boolean canonical, InputStream data) {

        Map<String, String> headers = new HashMap<>();
        if (artifactId != null) {
            headers.put(Headers.ARTIFACT_ID, artifactId);
        }
        if (artifactType != null) {
            headers.put(Headers.ARTIFACT_TYPE, artifactType.name());
        }
        if (version != null) {
            headers.put(Headers.VERSION, version);
        }
        Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();

        return requestHandler.sendRequest(new Request.RequestBuilder<ArtifactMetaData>()
                .operation(POST)
                .path(GROUP_BASE_PATH)
                .headers(headers)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseClass(ArtifactMetaData.class)
                .data(data)
                .build());
    }

    @Override
    public void deleteArtifactsInGroup(String groupId) {

        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(GROUP_BASE_PATH)
                .pathParams(List.of(groupId))
                .responseClass(Void.class)
                .build());
    }

    @Override
    public InputStream getContentById(long contentId) {

        return requestHandler.sendRequest(new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(IDS_CONTENT_ID)
                .pathParams(List.of(String.valueOf(contentId)))
                .responseClass(InputStream.class)
                .build());
    }

    @Override
    public InputStream getContentByGlobalId(long globalId) {

        return requestHandler.sendRequest(new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(IDS_GLOBAL_ID)
                .pathParams(List.of(String.valueOf(globalId)))
                .responseClass(InputStream.class)
                .build());
    }

    @Override
    public InputStream getContentByHash(String contentHash, Boolean canonical) {

        Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();

        return requestHandler.sendRequest(new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(IDS_CONTENT_HASH)
                .pathParams(List.of(String.valueOf(contentHash)))
                .queryParams(queryParams)
                .responseClass(InputStream.class)
                .build());
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String group, String name, String
            description, List<String> labels, List<String> properties, SortBy orderBy, SortOrder order, Integer offset, Integer limit) {

        final Map<String, List<String>> queryParams = new HashMap<>();
        if (name != null) {
            queryParams.put(Parameters.NAME, Collections.singletonList(name));
        }

        if (description != null) {
            queryParams.put(Parameters.DESCRIPTION, Collections.singletonList(description));
        }

        if (group != null) {
            queryParams.put(Parameters.GROUP, Collections.singletonList(group));
        }
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);
        if (labels != null && !labels.isEmpty()) {
            queryParams.put(Parameters.LABELS, labels);
        }
        if (properties != null && !properties.isEmpty()) {
            queryParams.put(Parameters.PROPERTIES, properties);
        }
        return requestHandler.sendRequest(new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(GET)
                .path(SEARCH_ARTIFACTS)
                .responseClass(ArtifactSearchResults.class)
                .queryParams(queryParams)
                .build());
    }

    @Override
    public ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order,
                                                          Integer offset, Integer limit) {

        final Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);

        return requestHandler.sendRequest(new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(POST)
                .path(SEARCH_ARTIFACTS)
                .responseClass(ArtifactSearchResults.class)
                .queryParams(queryParams)
                .data(data)
                .build());
    }

    @Override
    public List<RuleType> listGlobalRules() {

        return requestHandler.sendRequest(new Request.RequestBuilder<List>()
                .operation(GET)
                .path(RULES_BASE_PATH)
                .responseClass(List.class)
                .build());
    }

    @Override
    public void createGlobalRule(Rule data) {

        try {
            requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                    .operation(POST)
                    .path(RULES_BASE_PATH)
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseClass(Void.class)
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public void deleteAllGlobalRules() {

        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(RULES_BASE_PATH)
                .responseClass(Void.class)
                .build());
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {

        return requestHandler.sendRequest(new Request.RequestBuilder<Rule>()
                .operation(GET)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseClass(Rule.class)
                .build());
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {

        try {
            return requestHandler.sendRequest(new Request.RequestBuilder<Rule>()
                    .operation(PUT)
                    .path(RULE_PATH)
                    .pathParams(List.of(rule.value()))
                    .responseClass(Rule.class)
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {

        requestHandler.sendRequest(new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseClass(Void.class)
                .build());
    }

    @Override
    public List<NamedLogConfiguration> listLogConfigurations() {

        return requestHandler.sendRequest(new Request.RequestBuilder<List>()
                .operation(GET)
                .path(LOGS_BASE_PATH)
                .responseClass(List.class)
                .build());
    }

    @Override
    public NamedLogConfiguration getLogConfiguration(String logger) {

        return requestHandler.sendRequest(new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(GET)
                .path(LOG_PATH)
                .pathParams(List.of(logger))
                .responseClass(NamedLogConfiguration.class)
                .build());
    }

    @Override
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {

        try {
            return requestHandler.sendRequest(new Request.RequestBuilder<NamedLogConfiguration>()
                    .operation(PUT)
                    .path(LOG_PATH)
                    .pathParams(List.of(logger))
                    .responseClass(NamedLogConfiguration.class)
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .build());
        } catch (JsonProcessingException e) {
            throw parseError(e);
        }
    }

    @Override
    public NamedLogConfiguration removeLogConfiguration(String logger) {

        return requestHandler.sendRequest(new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(DELETE)
                .path(LOG_PATH)
                .pathParams(List.of(logger))
                .responseClass(NamedLogConfiguration.class)
                .build());
    }

    private void checkCommonQueryParams(SortBy orderBy, SortOrder order, Integer limit, Integer offset,
                                        Map<String, List<String>> queryParams) {
        if (offset != null) {
            queryParams.put(Parameters.OFFSET, Collections.singletonList(String.valueOf(offset)));
        }
        if (limit != null) {
            queryParams.put(Parameters.LIMIT, Collections.singletonList(String.valueOf(limit)));
        }
        if (order != null) {
            queryParams.put(Parameters.SORT_ORDER, Collections.singletonList(order.value()));
        }
        if (orderBy != null) {
            queryParams.put(Parameters.ORDER_BY, Collections.singletonList(orderBy.value()));
        }
    }

    private RestClientException parseError(Exception ex) {
        //FIXME proper error handling
        return new RestClientException(new Error());
    }
}