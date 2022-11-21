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

package io.apicurio.tests;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.apicurio.registry.rest.v2.beans.ArtifactOwner;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.GroupSearchResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
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
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;

/**
 * @author Fabian Martinez
 */
public class LoadBalanceRegistryClient implements RegistryClient {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private LinkedList<RegistryClientHolder> targets;

    public class RegistryClientHolder {
        public RegistryClient client;
        public String host;
    }

    /**
     * Constructor.
     * @param hosts
     */
    public LoadBalanceRegistryClient(List<String> hosts) {

        this.targets = new LinkedList<>();

        hosts.stream()
            .forEach(h -> {
                RegistryClientHolder c = new RegistryClientHolder();
                c.client = RegistryClientFactory.create(h);
                c.host = h;
                targets.add(c);
            });

    }

    public List<RegistryClientHolder> getRegistryNodes() {
        return targets;
    }

    private synchronized RegistryClient getTarget() {
        int randomElementIndex = ThreadLocalRandom.current().nextInt(this.targets.size());
        RegistryClientHolder t = this.targets.get(randomElementIndex);
        logger.trace("Request to {}", t.host);
        return t.client;
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getLatestArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public InputStream getLatestArtifact(String groupId, String artifactId) {
        return getTarget().getLatestArtifact(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifact(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String version, String name, String description, String contentType, InputStream data) {
        return getTarget().updateArtifact(groupId, artifactId, version, name, description, contentType, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @param references 
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifact(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.io.InputStream, java.util.List)
     */
    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String version, String name, String description, InputStream data, List<ArtifactReference> references) {
        return getTarget().updateArtifact(groupId, artifactId, version, name, description, data, references);
    }

    /**
     * @param groupId
     * @param artifactId
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        getTarget().deleteArtifact(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        return getTarget().getArtifactMetaData(groupId, artifactId);
    }

    @Override
    public ArtifactOwner getArtifactOwner(String groupId, String artifactId) {
        return getTarget().getArtifactOwner(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        getTarget().updateArtifactMetaData(groupId, artifactId, data);
    }

    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwner owner) {
        getTarget().updateArtifactOwner(groupId, artifactId, owner);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param canonical
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            Boolean canonical, String contentType, InputStream data) {
        return getTarget().getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, contentType, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            InputStream data) {
        return getTarget().getArtifactVersionMetaDataByContent(groupId, artifactId, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#listArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        return getTarget().listArtifactRules(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        getTarget().createArtifactRule(groupId, artifactId, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        getTarget().deleteArtifactRules(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return getTarget().getArtifactRuleConfig(groupId, artifactId, rule);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        return getTarget().updateArtifactRuleConfig(groupId, artifactId, rule, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        getTarget().deleteArtifactRule(groupId, artifactId, rule);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        getTarget().updateArtifactState(groupId, artifactId, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#testUpdateArtifact(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public void testUpdateArtifact(String groupId, String artifactId, String contentType, InputStream data) {
        getTarget().testUpdateArtifact(groupId, artifactId, contentType, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
        return getTarget().getArtifactVersion(groupId, artifactId, version);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return getTarget().getArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableMetaData data) {
        getTarget().updateArtifactVersionMetaData(groupId, artifactId, version, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        getTarget().deleteArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#updateArtifactVersionState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateState)
     */
    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            UpdateState data) {
        getTarget().updateArtifactVersionState(groupId, artifactId, version, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param offset
     * @param limit
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#listArtifactVersions(java.lang.String, java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset,
            Integer limit) {
        return getTarget().listArtifactVersions(groupId, artifactId, offset, limit);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String version, String name, String description, String contentType,
            InputStream data) {
        return getTarget().createArtifactVersion(groupId, artifactId, version, name, description,contentType, data);
    }

    /**
     * @param groupId
     * @param orderBy
     * @param order
     * @param offset
     * @param limit
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#listArtifactsInGroup(java.lang.String, io.apicurio.registry.rest.v2.beans.SortBy, io.apicurio.registry.rest.v2.beans.SortOrder, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order,
            Integer offset, Integer limit) {
        return getTarget().listArtifactsInGroup(groupId, orderBy, order, offset, limit);
    }

    /**
     * @param groupId
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#listArtifactsInGroup(java.lang.String)
     */
    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId) {
        return getTarget().listArtifactsInGroup(groupId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param artifactType
     * @param ifExists
     * @param canonical
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.rest.v2.beans.IfExists, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String version,
            String artifactType, IfExists ifExists, Boolean canonical, String name, String description, String contentType, String fromURL, String artifactSHA, InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, version, artifactType, ifExists, canonical, name, description, contentType, fromURL, artifactSHA, data);
    }

    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, String artifactType, IfExists ifExists, Boolean canonical, String artifactName, String artifactDescription, String contentType, String fromURL, String artifactSHA, InputStream data, List<ArtifactReference> artifactReferences) {
        return getTarget().createArtifact(groupId, artifactId, version, artifactType, ifExists, canonical, artifactName, artifactDescription, contentType, fromURL, artifactSHA, data, artifactReferences);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifact(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param data
     * @param references
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifact(java.lang.String,  java.lang.String, java.io.InputStream, java.util.List)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, InputStream data, List<ArtifactReference> references) {
        return getTarget().createArtifact(groupId, artifactId, data, references);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifact(java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifactWithVersion(String groupId, String artifactId, String version,
            InputStream data) {
        return getTarget().createArtifactWithVersion(groupId, artifactId, version, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifact(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String artifactType,
            InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, artifactType, data);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param artifactType
     * @param ifExists
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#createArtifact(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.rest.v2.beans.IfExists, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String artifactType,
            IfExists ifExists, InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, artifactType, ifExists, data);
    }

    /**
     * @param groupId
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteArtifactsInGroup(java.lang.String)
     */
    @Override
    public void deleteArtifactsInGroup(String groupId) {
        getTarget().deleteArtifactsInGroup(groupId);
    }

    @Override
    public void createArtifactGroup(GroupMetaData groupMetaData) {
        getTarget().createArtifactGroup(groupMetaData);
    }

    @Override
    public void deleteArtifactGroup(String groupId) {
        getTarget().deleteArtifactGroup(groupId);
    }

    @Override
    public GroupMetaData getArtifactGroup(String groupId) {
        return getTarget().getArtifactGroup(groupId);
    }

    @Override
    public GroupSearchResults listGroups(SortBy orderBy, SortOrder order, Integer offset, Integer limit) {
        return getTarget().listGroups(orderBy, order, offset, limit);
    }

    /**
     * @param contentId
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getContentById(long)
     */
    @Override
    public InputStream getContentById(long contentId) {
        return getTarget().getContentById(contentId);
    }

    /**
     * @param globalId
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getContentByGlobalId(long)
     */
    @Override
    public InputStream getContentByGlobalId(long globalId) {
        return getTarget().getContentByGlobalId(globalId);
    }

    @Override
    public InputStream getContentByGlobalId(long globalId, Boolean canonical, Boolean dereference) {
        return getTarget().getContentByGlobalId(globalId, canonical, dereference);
    }

    /**
     * @param contentHash
     * @param canonical
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getContentByHash(java.lang.String, java.lang.Boolean)
     */
    @Override
    public InputStream getContentByHash(String contentHash, Boolean canonical) {
        return getTarget().getContentByHash(contentHash, canonical);
    }

    /**
     * @param contentHash
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getContentByHash(java.lang.String)
     */
    @Override
    public InputStream getContentByHash(String contentHash) {
        return getTarget().getContentByHash(contentHash);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#searchArtifacts(java.lang.String, java.lang.String, java.lang.String, java.util.List, java.util.List, java.lang.Long, java.lang.Long, io.apicurio.registry.rest.v2.beans.SortBy, io.apicurio.registry.rest.v2.beans.SortOrder, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String group, String name, String description,
            List<String> labels, List<String> properties, Long globalId, Long contentId, SortBy orderBy,
            SortOrder order, Integer offset, Integer limit) {
        return getTarget().searchArtifacts(group, name, description, labels, properties, globalId, contentId, orderBy, order, offset, limit);
    }

    /**
     * @param data
     * @param orderBy
     * @param order
     * @param offset
     * @param limit
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#searchArtifactsByContent(java.io.InputStream, io.apicurio.registry.rest.v2.beans.SortBy, io.apicurio.registry.rest.v2.beans.SortOrder, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order,
            Integer offset, Integer limit) {
        return getTarget().searchArtifactsByContent(data, orderBy, order, offset, limit);
    }

    /**
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#listGlobalRules()
     */
    @Override
    public List<RuleType> listGlobalRules() {
        return getTarget().listGlobalRules();
    }

    /**
     * @param data
     * @see io.apicurio.registry.rest.client.RegistryClient#createGlobalRule(io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public void createGlobalRule(Rule data) {
        getTarget().createGlobalRule(data);
    }

    /**
     *
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        getTarget().deleteAllGlobalRules();
    }

    /**
     * @param rule
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return getTarget().getGlobalRuleConfig(rule);
    }

    /**
     * @param rule
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        return getTarget().updateGlobalRuleConfig(rule, data);
    }

    /**
     * @param rule
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) {
        getTarget().deleteGlobalRule(rule);
    }

    /**
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#listLogConfigurations()
     */
    @Override
    public List<NamedLogConfiguration> listLogConfigurations() {
        return getTarget().listLogConfigurations();
    }

    /**
     * @param logger
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getLogConfiguration(java.lang.String)
     */
    @Override
    public NamedLogConfiguration getLogConfiguration(String logger) {
        return getTarget().getLogConfiguration(logger);
    }

    /**
     * @param logger
     * @param data
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#setLogConfiguration(java.lang.String, io.apicurio.registry.rest.v2.beans.LogConfiguration)
     */
    @Override
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {
        return getTarget().setLogConfiguration(logger, data);
    }

    /**
     * @param logger
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#removeLogConfiguration(java.lang.String)
     */
    @Override
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        return getTarget().removeLogConfiguration(logger);
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByGlobalId(long globalId) {
        return getTarget().getArtifactReferencesByGlobalId(globalId);
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByContentId(long contentId) {
        return getTarget().getArtifactReferencesByContentId(contentId);
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByContentHash(String contentHash) {
        return getTarget().getArtifactReferencesByContentHash(contentHash);
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByCoordinates(String groupId, String artifactId, String version) {
        return getTarget().getArtifactReferencesByCoordinates(groupId, artifactId, version);
    }

    /**
     * @param requestHeaders
     * @see io.apicurio.registry.rest.client.RegistryClient#setNextRequestHeaders(java.util.Map)
     */
    @Override
    public void setNextRequestHeaders(Map<String, String> requestHeaders) {
        getTarget().setNextRequestHeaders(requestHeaders);
    }

    /**
     * @return
     * @see io.apicurio.registry.rest.client.RegistryClient#getHeaders()
     */
    @Override
    public Map<String, String> getHeaders() {
        return getTarget().getHeaders();
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#exportData()
     */
    @Override
    public InputStream exportData() {
        return getTarget().exportData();
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#importData(java.io.InputStream)
     */
    @Override
    public void importData(InputStream data) {
        getTarget().importData(data);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#importData(java.io.InputStream, boolean, boolean)
     */
    @Override
    public void importData(InputStream data, boolean preserveGlobalIds, boolean preserveContentIds) {
        getTarget().importData(data, preserveGlobalIds, preserveContentIds);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#createRoleMapping(io.apicurio.registry.rest.v2.beans.RoleMapping)
     */
    @Override
    public void createRoleMapping(RoleMapping data) {
        getTarget().createRoleMapping(data);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteRoleMapping(java.lang.String)
     */
    @Override
    public void deleteRoleMapping(String principalId) {
        getTarget().deleteRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#getRoleMapping(java.lang.String)
     */
    @Override
    public RoleMapping getRoleMapping(String principalId) {
        return getTarget().getRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#listRoleMappings()
     */
    @Override
    public List<RoleMapping> listRoleMappings() {
        return getTarget().listRoleMappings();
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#updateRoleMapping(java.lang.String, io.apicurio.registry.types.RoleType)
     */
    @Override
    public void updateRoleMapping(String principalId, RoleType role) {
        getTarget().updateRoleMapping(principalId, role);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#listConfigProperties()
     */
    @Override
    public List<ConfigurationProperty> listConfigProperties() {
        return getTarget().listConfigProperties();
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#getConfigProperty(java.lang.String)
     */
    @Override
    public ConfigurationProperty getConfigProperty(String propertyName) {
        return getTarget().getConfigProperty(propertyName);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#setConfigProperty(java.lang.String, java.lang.String)
     */
    @Override
    public void setConfigProperty(String propertyName, String propertyValue) {
        getTarget().setConfigProperty(propertyName, propertyValue);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteConfigProperty(java.lang.String)
     */
    @Override
    public void deleteConfigProperty(String propertyName) {
        getTarget().deleteConfigProperty(propertyName);
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#getCurrentUserInfo()
     */
    @Override
    public UserInfo getCurrentUserInfo() {
        return getTarget().getCurrentUserInfo();
    }

    @Override
    public void close() throws IOException {
        getTarget().close();
    }
}