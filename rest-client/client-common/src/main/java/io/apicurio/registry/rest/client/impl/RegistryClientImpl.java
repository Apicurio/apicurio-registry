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

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.InvalidArtifactIdException;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.request.Parameters;
import io.apicurio.registry.rest.client.request.provider.AdminRequestsProvider;
import io.apicurio.registry.rest.client.request.provider.GroupRequestsProvider;
import io.apicurio.registry.rest.client.request.provider.IdRequestsProvider;
import io.apicurio.registry.rest.client.request.provider.SearchRequestsProvider;
import io.apicurio.registry.rest.client.request.provider.UsersRequestsProvider;
import io.apicurio.registry.rest.client.spi.RegistryHttpClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.UserInfo;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ArtifactIdValidator;


/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class RegistryClientImpl implements RegistryClient {

    private final RegistryHttpClient registryHttpClient;

    public RegistryClientImpl(RegistryHttpClient registryHttpClient) {
        this.registryHttpClient = registryHttpClient;
    }

    @Override
    public InputStream getLatestArtifact(String groupId, String artifactId) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.getLatestArtifact(normalizeGid(groupId), artifactId));
    }

    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, InputStream data) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.updateArtifact(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        registryHttpClient.sendRequest(GroupRequestsProvider.deleteArtifact(normalizeGid(groupId), artifactId));
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.getArtifactMetaData(normalizeGid(groupId), artifactId));
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        try {
            registryHttpClient.sendRequest(GroupRequestsProvider.updateArtifactMetaData(normalizeGid(groupId), artifactId, data));
        } catch (JsonProcessingException e) {
            throw new RestClientException(new Error());
        }
    }

    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data) {
        final Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();
        return registryHttpClient.sendRequest(GroupRequestsProvider.getArtifactVersionMetaDataByContent(normalizeGid(groupId), artifactId, queryParams, data));
    }

    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.listArtifactRules(normalizeGid(groupId), artifactId));
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        try {
            registryHttpClient.sendRequest(GroupRequestsProvider.createArtifactRule(normalizeGid(groupId), artifactId, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        registryHttpClient.sendRequest(GroupRequestsProvider.deleteArtifactRules(normalizeGid(groupId), artifactId));
    }

    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.getArtifactRuleConfig(normalizeGid(groupId), artifactId, rule));
    }

    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        try {
            return registryHttpClient.sendRequest(GroupRequestsProvider.updateArtifactRuleConfig(normalizeGid(groupId), artifactId, rule, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        registryHttpClient.sendRequest(GroupRequestsProvider.deleteArtifactRule(normalizeGid(groupId), artifactId, rule));
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        try {
            registryHttpClient.sendRequest(GroupRequestsProvider.updateArtifactState(normalizeGid(groupId), artifactId, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        registryHttpClient.sendRequest(GroupRequestsProvider.testUpdateArtifact(normalizeGid(groupId), artifactId, data));
    }

    @Override
    public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.getArtifactVersion(normalizeGid(groupId), artifactId, version));
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return registryHttpClient.sendRequest(GroupRequestsProvider.getArtifactVersionMetaData(normalizeGid(groupId), artifactId, version));
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) {
        try {
            registryHttpClient.sendRequest(GroupRequestsProvider.updateArtifactVersionMetaData(normalizeGid(groupId), artifactId, version, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        registryHttpClient.sendRequest(GroupRequestsProvider.deleteArtifactVersionMetaData(normalizeGid(groupId), artifactId, version));
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) {
        try {
            registryHttpClient.sendRequest(GroupRequestsProvider.updateArtifactVersionState(normalizeGid(groupId), artifactId, version, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset, Integer limit) {
        Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(null, null, limit, offset, queryParams);
        return registryHttpClient.sendRequest(GroupRequestsProvider.listArtifactVersions(normalizeGid(groupId), artifactId, queryParams));
    }

    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String version, InputStream data) {
        final Map<String, String> headers = version != null ? Map.of(Headers.VERSION, version) : Collections.emptyMap();
        return registryHttpClient.sendRequest(GroupRequestsProvider.createArtifactVersion(normalizeGid(groupId), artifactId, data, headers));
    }

    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order, Integer offset, Integer limit) {
        final Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);
        return registryHttpClient.sendRequest(GroupRequestsProvider.listArtifactsInGroup(normalizeGid(groupId), queryParams));
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
        return registryHttpClient.sendRequest(GroupRequestsProvider.createArtifact(normalizeGid(groupId), headers, data, queryParams));
    }

    @Override
    public void deleteArtifactsInGroup(String groupId) {
        registryHttpClient.sendRequest(GroupRequestsProvider.deleteArtifactsInGroup(normalizeGid(groupId)));
    }

    @Override
    public InputStream getContentById(long contentId) {
        return registryHttpClient.sendRequest(IdRequestsProvider.getContentById(contentId));
    }

    @Override
    public InputStream getContentByGlobalId(long globalId) {
        return registryHttpClient.sendRequest(IdRequestsProvider.getContentByGlobalId(globalId));
    }

    @Override
    public InputStream getContentByHash(String contentHash, Boolean canonical) {
        final Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();
        return registryHttpClient.sendRequest(IdRequestsProvider.getContentByHash(contentHash, canonical, queryParams));
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
        return registryHttpClient.sendRequest(SearchRequestsProvider.searchArtifacts(queryParams));
    }

    @Override
    public ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order,
                                                          Integer offset, Integer limit) {
        final Map<String, List<String>> queryParams = new HashMap<>();
        checkCommonQueryParams(orderBy, order, limit, offset, queryParams);
        return registryHttpClient.sendRequest(SearchRequestsProvider.searchArtifactsByContent(data, queryParams));
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return registryHttpClient.sendRequest(AdminRequestsProvider.listGlobalRules());
    }

    @Override
    public void createGlobalRule(Rule data) {
        try {
            registryHttpClient.sendRequest(AdminRequestsProvider.createGlobalRule(data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteAllGlobalRules() {
        registryHttpClient.sendRequest(AdminRequestsProvider.deleteAllGlobalRules());
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return registryHttpClient.sendRequest(AdminRequestsProvider.getGlobalRule(rule));
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        try {
            return registryHttpClient.sendRequest(AdminRequestsProvider.updateGlobalRuleConfig(rule, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        registryHttpClient.sendRequest(AdminRequestsProvider.deleteGlobalRule(rule));
    }

    @Override
    public List<NamedLogConfiguration> listLogConfigurations() {
        return registryHttpClient.sendRequest(AdminRequestsProvider.listLogConfigurations());
    }

    @Override
    public NamedLogConfiguration getLogConfiguration(String logger) {
        return registryHttpClient.sendRequest(AdminRequestsProvider.getLogConfiguration(logger));
    }

    @Override
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {
        try {
            return registryHttpClient.sendRequest(AdminRequestsProvider.setLogConfiguration(logger, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        return registryHttpClient.sendRequest(AdminRequestsProvider.removeLogConfiguration(logger));
    }

    @Override
    public InputStream exportData() {
        return registryHttpClient.sendRequest(AdminRequestsProvider.exportData());
    }

    @Override
    public void importData(InputStream data) {
        registryHttpClient.sendRequest(AdminRequestsProvider.importData(data));
    }

    @Override
    public void createRoleMapping(RoleMapping data) {
        try {
            registryHttpClient.sendRequest(AdminRequestsProvider.createRoleMapping(data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteRoleMapping(String principalId) {
        registryHttpClient.sendRequest(AdminRequestsProvider.deleteRoleMapping(principalId));
    }

    @Override
    public RoleMapping getRoleMapping(String principalId) {
        return registryHttpClient.sendRequest(AdminRequestsProvider.getRoleMapping(principalId));
    }

    @Override
    public List<RoleMapping> listRoleMappings() {
        return registryHttpClient.sendRequest(AdminRequestsProvider.listRoleMappings());
    }

    @Override
    public void updateRoleMapping(String principalId, RoleType role) {
        try {
            registryHttpClient.sendRequest(AdminRequestsProvider.updateRoleMapping(principalId, role));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public UserInfo getCurrentUserInfo() {
        return registryHttpClient.sendRequest(UsersRequestsProvider.getCurrentUserInfo());
    }

    @Override
    public void setNextRequestHeaders(Map<String, String> requestHeaders) {
        registryHttpClient.setNextRequestHeaders(requestHeaders);
    }

    @Override
    public Map<String, String> getHeaders() {
        return registryHttpClient.getHeaders();
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

    private static RestClientException parseSerializationError(JsonProcessingException ex) {
        final Error error = new Error();
        error.setName(ex.getClass().getSimpleName());
        error.setMessage(ex.getMessage());
        return new RestClientException(error);
    }
}