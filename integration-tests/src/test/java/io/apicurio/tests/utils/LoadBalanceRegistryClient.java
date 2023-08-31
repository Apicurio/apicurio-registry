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

package io.apicurio.tests.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactContent;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactOwner;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.Comment;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.GroupSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.NewComment;
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
 * @author Carles Arnal
 */
@SuppressWarnings("deprecation")
public class LoadBalanceRegistryClient implements RegistryClient {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private LinkedList<RegistryClientHolder> targets;

    public class RegistryClientHolder {
        public RegistryClient client;
        public String host;
    }

    /**
     * Constructor.
     *
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
     * @see RegistryClient#getLatestArtifact(String, String)
     */
    @Override
    public InputStream getLatestArtifact(String groupId, String artifactId) {
        return getTarget().getLatestArtifact(groupId, artifactId);
    }

    /**
     * @see RegistryClient#updateArtifact(String, String, InputStream)
     */
    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String version, String name, String description, String contentType, InputStream data) {
        return getTarget().updateArtifact(groupId, artifactId, version, name, description, contentType, data);
    }

    /**
     * @see RegistryClient#updateArtifact(String, String, String, String, String, InputStream, List)
     */
    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String version, String name, String description, InputStream data, List<ArtifactReference> references) {
        return getTarget().updateArtifact(groupId, artifactId, version, name, description, data, references);
    }

    /**
     * @see RegistryClient#deleteArtifact(String, String)
     */
    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        getTarget().deleteArtifact(groupId, artifactId);
    }

    /**
     * @see RegistryClient#getArtifactMetaData(String, String)
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
     * @see RegistryClient#updateArtifactMetaData(String, String, EditableMetaData)
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
     * @see RegistryClient#getArtifactVersionMetaDataByContent(String, String, Boolean, InputStream)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
                                                               Boolean canonical, String contentType, InputStream data) {
        return getTarget().getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, contentType, data);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, ArtifactContent artifactContent) {
        return getTarget().getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, artifactContent);
    }

    /**
     * @see RegistryClient#getArtifactVersionMetaDataByContent(String, String, InputStream)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
                                                               InputStream data) {
        return getTarget().getArtifactVersionMetaDataByContent(groupId, artifactId, data);
    }

    /**
     * @see RegistryClient#listArtifactRules(String, String)
     */
    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        return getTarget().listArtifactRules(groupId, artifactId);
    }

    /**
     * @see RegistryClient#createArtifactRule(String, String, Rule)
     */
    @Override
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        getTarget().createArtifactRule(groupId, artifactId, data);
    }

    /**
     * @see RegistryClient#deleteArtifactRules(String, String)
     */
    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        getTarget().deleteArtifactRules(groupId, artifactId);
    }

    /**
     * @see RegistryClient#getArtifactRuleConfig(String, String, RuleType)
     */
    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return getTarget().getArtifactRuleConfig(groupId, artifactId, rule);
    }

    /**
     * @see RegistryClient#updateArtifactRuleConfig(String, String, RuleType, Rule)
     */
    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        return getTarget().updateArtifactRuleConfig(groupId, artifactId, rule, data);
    }

    /**
     * @see RegistryClient#deleteArtifactRule(String, String, RuleType)
     */
    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        getTarget().deleteArtifactRule(groupId, artifactId, rule);
    }

    /**
     * @see RegistryClient#updateArtifactState(String, String, UpdateState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        getTarget().updateArtifactState(groupId, artifactId, data);
    }

    /**
     * @see RegistryClient#testUpdateArtifact(String, String, InputStream)
     */
    @Override
    public void testUpdateArtifact(String groupId, String artifactId, String contentType, InputStream data) {
        getTarget().testUpdateArtifact(groupId, artifactId, contentType, data);
    }

    /**
     * @see RegistryClient#getArtifactVersion(String, String, String)
     */
    @Override
    public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
        return getTarget().getArtifactVersion(groupId, artifactId, version);
    }

    /**
     * @see RegistryClient#getArtifactVersionMetaData(String, String, String)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return getTarget().getArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @see RegistryClient#updateArtifactVersionMetaData(String, String, String, EditableMetaData)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
                                              EditableMetaData data) {
        getTarget().updateArtifactVersionMetaData(groupId, artifactId, version, data);
    }

    /**
     * @see RegistryClient#deleteArtifactVersionMetaData(String, String, String)
     */
    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        getTarget().deleteArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @see RegistryClient#updateArtifactVersionState(String, String, String, UpdateState)
     */
    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
                                           UpdateState data) {
        getTarget().updateArtifactVersionState(groupId, artifactId, version, data);
    }

    /**
     * @see RegistryClient#listArtifactVersions(String, String, Integer, Integer)
     */
    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset,
                                                     Integer limit) {
        return getTarget().listArtifactVersions(groupId, artifactId, offset, limit);
    }

    /**
     * @see RegistryClient#createArtifactVersion(String, String, String, InputStream)
     */
    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String version, String name, String description, String contentType,
                                                 InputStream data) {
        return getTarget().createArtifactVersion(groupId, artifactId, version, name, description, contentType, data);
    }

    /**
     * @see RegistryClient#listArtifactsInGroup(String, SortBy, SortOrder, Integer, Integer)
     */
    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order,
                                                      Integer offset, Integer limit) {
        return getTarget().listArtifactsInGroup(groupId, orderBy, order, offset, limit);
    }

    /**
     * @see RegistryClient#listArtifactsInGroup(String)
     */
    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId) {
        return getTarget().listArtifactsInGroup(groupId);
    }

    /**
     * @see RegistryClient#createArtifact(String, String, String, io.apicurio.registry.types.ArtifactType, IfExists, Boolean, InputStream)
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
     * @see RegistryClient#createArtifact(String, String, InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, data);
    }

    /**
     * @see RegistryClient#createArtifact(String, String, InputStream, List)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, InputStream data, List<ArtifactReference> references) {
        return getTarget().createArtifact(groupId, artifactId, data, references);
    }

    /**
     * @see RegistryClient#createArtifact(String, String, String, InputStream)
     */
    @Override
    public ArtifactMetaData createArtifactWithVersion(String groupId, String artifactId, String version,
                                                      InputStream data) {
        return getTarget().createArtifactWithVersion(groupId, artifactId, version, data);
    }

    /**
     * @see RegistryClient#createArtifact(String, String, io.apicurio.registry.types.ArtifactType, InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String artifactType,
                                           InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, artifactType, data);
    }

    /**
     * @see RegistryClient#createArtifact(String, String, io.apicurio.registry.types.ArtifactType, IfExists, InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String artifactType,
                                           IfExists ifExists, InputStream data) {
        return getTarget().createArtifact(groupId, artifactId, artifactType, ifExists, data);
    }

    /**
     * @see RegistryClient#deleteArtifactsInGroup(String)
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
     * @see RegistryClient#getContentById(long)
     */
    @Override
    public InputStream getContentById(long contentId) {
        return getTarget().getContentById(contentId);
    }

    /**
     * @see RegistryClient#getContentByGlobalId(long)
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
     * @see RegistryClient#getContentByHash(String, Boolean)
     */
    @Override
    public InputStream getContentByHash(String contentHash, Boolean canonical) {
        return getTarget().getContentByHash(contentHash, canonical);
    }

    /**
     * @see RegistryClient#getContentByHash(String)
     */
    @Override
    public InputStream getContentByHash(String contentHash) {
        return getTarget().getContentByHash(contentHash);
    }

    /**
     * @see RegistryClient#searchArtifacts(String, String, String, List, List, Long, Long, SortBy, SortOrder, Integer, Integer)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String group, String name, String description,
                                                 List<String> labels, List<String> properties, Long globalId, Long contentId, SortBy orderBy,
                                                 SortOrder order, Integer offset, Integer limit) {
        return getTarget().searchArtifacts(group, name, description, labels, properties, globalId, contentId, orderBy, order, offset, limit);
    }

    /**
     * @see RegistryClient#searchArtifactsByContent(InputStream, SortBy, SortOrder, Integer, Integer)
     */
    @Override
    public ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order,
                                                          Integer offset, Integer limit) {
        return getTarget().searchArtifactsByContent(data, orderBy, order, offset, limit);
    }

    /**
     * @see RegistryClient#listGlobalRules()
     */
    @Override
    public List<RuleType> listGlobalRules() {
        return getTarget().listGlobalRules();
    }

    /**
     * @see RegistryClient#createGlobalRule(Rule)
     */
    @Override
    public void createGlobalRule(Rule data) {
        getTarget().createGlobalRule(data);
    }

    /**
     * @see RegistryClient#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        getTarget().deleteAllGlobalRules();
    }

    /**
     * @see RegistryClient#getGlobalRuleConfig(RuleType)
     */
    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return getTarget().getGlobalRuleConfig(rule);
    }

    /**
     * @see RegistryClient#updateGlobalRuleConfig(RuleType, Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        return getTarget().updateGlobalRuleConfig(rule, data);
    }

    /**
     * @see RegistryClient#deleteGlobalRule(RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) {
        getTarget().deleteGlobalRule(rule);
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
     * @see RegistryClient#setNextRequestHeaders(Map)
     */
    @Override
    public void setNextRequestHeaders(Map<String, String> requestHeaders) {
        getTarget().setNextRequestHeaders(requestHeaders);
    }

    /**
     * @see RegistryClient#getHeaders()
     */
    @Override
    public Map<String, String> getHeaders() {
        return getTarget().getHeaders();
    }

    /**
     * @see RegistryClient#exportData()
     */
    @Override
    public InputStream exportData() {
        return getTarget().exportData();
    }

    /**
     * @see RegistryClient#importData(InputStream)
     */
    @Override
    public void importData(InputStream data) {
        getTarget().importData(data);
    }

    /**
     * @see RegistryClient#importData(InputStream, boolean, boolean)
     */
    @Override
    public void importData(InputStream data, boolean preserveGlobalIds, boolean preserveContentIds) {
        getTarget().importData(data, preserveGlobalIds, preserveContentIds);
    }

    /**
     * @see RegistryClient#createRoleMapping(RoleMapping)
     */
    @Override
    public void createRoleMapping(RoleMapping data) {
        getTarget().createRoleMapping(data);
    }

    /**
     * @see RegistryClient#deleteRoleMapping(String)
     */
    @Override
    public void deleteRoleMapping(String principalId) {
        getTarget().deleteRoleMapping(principalId);
    }

    /**
     * @see RegistryClient#getRoleMapping(String)
     */
    @Override
    public RoleMapping getRoleMapping(String principalId) {
        return getTarget().getRoleMapping(principalId);
    }

    /**
     * @see RegistryClient#listRoleMappings()
     */
    @Override
    public List<RoleMapping> listRoleMappings() {
        return getTarget().listRoleMappings();
    }

    /**
     * @see RegistryClient#updateRoleMapping(String, RoleType)
     */
    @Override
    public void updateRoleMapping(String principalId, RoleType role) {
        getTarget().updateRoleMapping(principalId, role);
    }

    /**
     * @see RegistryClient#listConfigProperties()
     */
    @Override
    public List<ConfigurationProperty> listConfigProperties() {
        return getTarget().listConfigProperties();
    }

    /**
     * @see RegistryClient#getConfigProperty(String)
     */
    @Override
    public ConfigurationProperty getConfigProperty(String propertyName) {
        return getTarget().getConfigProperty(propertyName);
    }

    /**
     * @see RegistryClient#setConfigProperty(String, String)
     */
    @Override
    public void setConfigProperty(String propertyName, String propertyValue) {
        getTarget().setConfigProperty(propertyName, propertyValue);
    }

    /**
     * @see RegistryClient#deleteConfigProperty(String)
     */
    @Override
    public void deleteConfigProperty(String propertyName) {
        getTarget().deleteConfigProperty(propertyName);
    }

    /**
     * @see RegistryClient#getCurrentUserInfo()
     */
    @Override
    public UserInfo getCurrentUserInfo() {
        return getTarget().getCurrentUserInfo();
    }

    /**
     * @see RegistryClient#getArtifactVersionComments(String, String, String)
     */
    @Override
    public List<Comment> getArtifactVersionComments(String groupId, String artifactId, String version) {
        return getTarget().getArtifactVersionComments(groupId, artifactId, version);
    }

    /**
     * @see RegistryClient#addArtifactVersionComment(String, String, String, NewComment)
     */
    @Override
    public Comment addArtifactVersionComment(String groupId, String artifactId, String version, NewComment comment) {
        return getTarget().addArtifactVersionComment(groupId, artifactId, version, comment);
    }

    /**
     * @see RegistryClient#deleteArtifactVersionComment(String, String, String, String)
     */
    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        getTarget().deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }

    /**
     * @see RegistryClient#editArtifactVersionComment(String, String, String, String, NewComment)
     */
    @Override
    public void editArtifactVersionComment(String groupId, String artifactId, String version, String commentId, NewComment comment) {
        getTarget().editArtifactVersionComment(groupId, artifactId, version, commentId, comment);
    }

    @Override
    public void close() throws IOException {
        getTarget().close();
    }
}