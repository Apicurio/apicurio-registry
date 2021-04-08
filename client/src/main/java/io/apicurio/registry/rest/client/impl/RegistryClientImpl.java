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

import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.InvalidArtifactIdException;
import io.apicurio.registry.rest.client.request.RequestHandler;
import io.apicurio.registry.rest.client.request.provider.AdminRequestsProvider;
import io.apicurio.registry.rest.client.request.provider.GroupRequestsProvider;
import io.apicurio.registry.rest.client.request.provider.IdRequestsProvider;
import io.apicurio.registry.rest.client.request.Parameters;
import io.apicurio.registry.rest.client.request.provider.SearchRequestsProvider;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
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
import io.apicurio.registry.utils.ArtifactIdValidator;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class RegistryClientImpl implements RegistryClient {

    private final RequestHandler requestHandler;

    public RegistryClientImpl(String endpoint) {
        this(endpoint, Collections.emptyMap(), null);
    }

    public RegistryClientImpl(String endpoint, Map<String, Object> configs) {
        this(endpoint, configs, null);
    }

    public RegistryClientImpl(String endpoint, Map<String, Object> configs, Auth auth) {
        requestHandler = new RequestHandler(endpoint, configs, auth);
    }

    @Override
    public InputStream getLatestArtifact(String groupId, String artifactId) {
        return requestHandler.sendRequest(GroupRequestsProvider.getLatestArtifact(normalizeGid(groupId), artifactId));
    }

    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream data) {
        return requestHandler.sendRequest(GroupRequestsProvider.updateArtifact(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        requestHandler.sendRequest(GroupRequestsProvider.deleteArtifact(normalizeGid(groupId), artifactId));
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        return requestHandler.sendRequest(GroupRequestsProvider.getArtifactMetaData(normalizeGid(groupId), artifactId));
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        requestHandler.sendRequest(GroupRequestsProvider.updateArtifactMetaData(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data) {
        final Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();
        return requestHandler.sendRequest(GroupRequestsProvider.getArtifactVersionMetaDataByContent(normalizeGid(groupId), artifactId, queryParams, data));
    }

    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        return requestHandler.sendRequest(GroupRequestsProvider.listArtifactRules(normalizeGid(groupId), artifactId));
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        requestHandler.sendRequest(GroupRequestsProvider.createArtifactRule(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        requestHandler.sendRequest(GroupRequestsProvider.deleteArtifactRules(normalizeGid(groupId), artifactId));
    }

    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return requestHandler.sendRequest(GroupRequestsProvider.getArtifactRuleConfig(normalizeGid(groupId), artifactId, rule));
    }

    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        return requestHandler.sendRequest(GroupRequestsProvider.updateArtifactRuleConfig(normalizeGid(groupId), artifactId, rule, data));
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        requestHandler.sendRequest(GroupRequestsProvider.deleteArtifactRule(normalizeGid(groupId), artifactId, rule));
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        requestHandler.sendRequest(GroupRequestsProvider.updateArtifactState(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        requestHandler.sendRequest(GroupRequestsProvider.testUpdateArtifact(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
        return requestHandler.sendRequest(GroupRequestsProvider.getArtifactVersion(normalizeGid(groupId), artifactId, version));
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return requestHandler.sendRequest(GroupRequestsProvider.getArtifactVersionMetaData(normalizeGid(groupId), artifactId, version));
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) {
        requestHandler.sendRequest(GroupRequestsProvider.updateArtifactVersionMetaData(normalizeGid(groupId), artifactId, version, data));
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        requestHandler.sendRequest(GroupRequestsProvider.deleteArtifactVersionMetaData(normalizeGid(groupId), artifactId, version));
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) {
        requestHandler.sendRequest(GroupRequestsProvider.updateArtifactVersionState(normalizeGid(groupId), artifactId, version, data));
    }

    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset, Integer limit) {
        Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(null, null, limit, offset, queryParams);
        return requestHandler.sendRequest(GroupRequestsProvider.listArtifactVersions(normalizeGid(groupId), artifactId, queryParams));
    }

    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String version, InputStream data) {
        final Map<String, String> headers = version != null ? Map.of(Headers.VERSION, version) : Collections.emptyMap();
        return requestHandler.sendRequest(GroupRequestsProvider.createArtifactVersion(normalizeGid(groupId), artifactId, data, headers));
    }

    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order, Integer offset, Integer limit) {
        final Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);
        return requestHandler.sendRequest(GroupRequestsProvider.listArtifactsInGroup(normalizeGid(groupId), queryParams));
    }

    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, IfExists ifExists, Boolean canonical, InputStream data) {
        if (artifactId != null && !ArtifactIdValidator.isArtifactIdAllowed(artifactId)) {
            throw new InvalidArtifactIdException();
        }
        final Map<String, String> headers = new HashMap<>();
        if (artifactId != null) {
            headers.put(Headers.ARTIFACT_ID, artifactId);
        }
        if (artifactType != null) {
            headers.put(Headers.ARTIFACT_TYPE, artifactType.name());
        }
        if (version != null) {
            headers.put(Headers.VERSION, version);
        }

        final Map<String, List<String>> queryParams = new HashMap<>();
        if (canonical != null) {
            queryParams.put(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical)));
        }
        if (ifExists != null) {
            queryParams.put(Parameters.IF_EXISTS, Collections.singletonList(ifExists.value()));
        }
        return requestHandler.sendRequest(GroupRequestsProvider.createArtifact(normalizeGid(groupId), headers, data, queryParams));
    }

    @Override
    public void deleteArtifactsInGroup(String groupId) {
        requestHandler.sendRequest(GroupRequestsProvider.deleteArtifactsInGroup(normalizeGid(groupId)));
    }

    @Override
    public InputStream getContentById(long contentId) {
        return requestHandler.sendRequest(IdRequestsProvider.getContentById(contentId));
    }

    @Override
    public InputStream getContentByGlobalId(long globalId) {
        return requestHandler.sendRequest(IdRequestsProvider.getContentByGlobalId(globalId));
    }

    @Override
    public InputStream getContentByHash(String contentHash, Boolean canonical) {
        final Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();
        return requestHandler.sendRequest(IdRequestsProvider.getContentByHash(contentHash, canonical, queryParams));
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
        return requestHandler.sendRequest(SearchRequestsProvider.searchArtifacts(queryParams));
    }

    @Override
    public ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order,
                                                          Integer offset, Integer limit) {
        final Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);
        return requestHandler.sendRequest(SearchRequestsProvider.searchArtifactsByContent(data, queryParams));
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return requestHandler.sendRequest(AdminRequestsProvider.listGlobalRules());
    }

    @Override
    public void createGlobalRule(Rule data) {
        requestHandler.sendRequest(AdminRequestsProvider.createGlobalRule(data));
    }

    @Override
    public void deleteAllGlobalRules() {
        requestHandler.sendRequest(AdminRequestsProvider.deleteAllGlobalRules());
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return requestHandler.sendRequest(AdminRequestsProvider.getGlobalRule(rule));
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        return requestHandler.sendRequest(AdminRequestsProvider.updateGlobalRuleConfig(rule, data));
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        requestHandler.sendRequest(AdminRequestsProvider.deleteGlobalRule(rule));
    }

    @Override
    public List<NamedLogConfiguration> listLogConfigurations() {
        return requestHandler.sendRequest(AdminRequestsProvider.listLogConfigurations());
    }

    @Override
    public NamedLogConfiguration getLogConfiguration(String logger) {
        return requestHandler.sendRequest(AdminRequestsProvider.getLogConfiguration(logger));
    }

    @Override
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {
        return requestHandler.sendRequest(AdminRequestsProvider.setLogConfiguration(logger, data));
    }

    @Override
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        return requestHandler.sendRequest(AdminRequestsProvider.removeLogConfiguration(logger));
    }

    @Override
    public InputStream exportData() {
        return requestHandler.sendRequest(AdminRequestsProvider.exportData());
    }

    @Override
    public void importData(InputStream data) {
        requestHandler.sendRequest(AdminRequestsProvider.importData(data));
    }

    @Override
    public void setNextRequestHeaders(Map<String, String> requestHeaders) {
        requestHandler.setNextRequestHeaders(requestHeaders);
    }

    @Override
    public Map<String, String> getHeaders() {
        return requestHandler.getHeaders();
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

    private String normalizeGid(String groupId) {
        return groupId == null ? "default" : groupId;
    }
}