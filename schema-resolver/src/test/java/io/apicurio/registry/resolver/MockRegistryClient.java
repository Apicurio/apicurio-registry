/*
 * Copyright 2023 Red Hat
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
package io.apicurio.registry.resolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.*;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;


public class MockRegistryClient implements RegistryClient {

    private String schemaContent;
    public int timesGetContentByHashCalled;

    public MockRegistryClient(String schemaContent) {
        this.schemaContent = schemaContent;
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

    @Override
    public InputStream getLatestArtifact(String groupId, String artifactId) {
        throw new UnsupportedOperationException("Unimplemented method 'getLatestArtifact'");
    }

    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String version, String artifactName,
            String artifactDescription, String contentType, InputStream data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifact'");
    }

    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String version, String artifactName,
            String artifactDescription, InputStream data,
            List<ArtifactReference> references) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifact'");
    }

    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifact'");
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactMetaData'");
    }

    @Override
    public ArtifactOwner getArtifactOwner(String groupId, String artifactId) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactOwner'");
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifactMetaData'");
    }

    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwner owner) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifactOwner'");
    }

    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical,
            String contentType, InputStream data) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactVersionMetaDataByContent'");
    }

    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, ArtifactContent artifactContent) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactVersionMetaDataByContent'");
    }

    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        throw new UnsupportedOperationException("Unimplemented method 'listArtifactRules'");
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        throw new UnsupportedOperationException("Unimplemented method 'createArtifactRule'");
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifactRules'");
    }

    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactRuleConfig'");
    }

    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifactRuleConfig'");
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifactRule'");
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifactState'");
    }

    @Override
    public void testUpdateArtifact(String groupId, String artifactId, String contentType, InputStream data) {
        throw new UnsupportedOperationException("Unimplemented method 'testUpdateArtifact'");
    }

    @Override
    public InputStream getArtifactVersion(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactVersion'");
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactVersionMetaData'");
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableMetaData data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifactVersionMetaData'");
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifactVersionMetaData'");
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateArtifactVersionState'");
    }

    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset,
            Integer limit) {
        throw new UnsupportedOperationException("Unimplemented method 'listArtifactVersions'");
    }

    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String version,
            String artifactName, String artifactDescription, String contentType, InputStream data) {
        throw new UnsupportedOperationException("Unimplemented method 'createArtifactVersion'");
    }

    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order,
            Integer offset, Integer limit) {
        throw new UnsupportedOperationException("Unimplemented method 'listArtifactsInGroup'");
    }

    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, String artifactType,
            IfExists ifExists, Boolean canonical, String artifactName, String artifactDescription,
            String contentType, String fromURL, String artifactSHA, InputStream data) {
        throw new UnsupportedOperationException("Unimplemented method 'createArtifact'");
    }

    @Override
    public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, String artifactType,
            IfExists ifExists, Boolean canonical, String artifactName, String artifactDescription,
            String contentType, String fromURL, String artifactSHA, InputStream data,
            List<ArtifactReference> artifactReferences) {
        throw new UnsupportedOperationException("Unimplemented method 'createArtifact'");
    }

    @Override
    public void deleteArtifactsInGroup(String groupId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifactsInGroup'");
    }

    @Override
    public void createArtifactGroup(GroupMetaData groupMetaData) {
        throw new UnsupportedOperationException("Unimplemented method 'createArtifactGroup'");
    }

    @Override
    public void deleteArtifactGroup(String groupId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifactGroup'");
    }

    @Override
    public GroupMetaData getArtifactGroup(String groupId) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactGroup'");
    }

    @Override
    public GroupSearchResults listGroups(SortBy orderBy, SortOrder order, Integer offset, Integer limit) {
        throw new UnsupportedOperationException("Unimplemented method 'listGroups'");
    }

    @Override
    public InputStream getContentById(long contentId) {
        throw new UnsupportedOperationException("Unimplemented method 'getContentById'");
    }

    @Override
    public InputStream getContentByGlobalId(long globalId) {
        throw new UnsupportedOperationException("Unimplemented method 'getContentByGlobalId'");
    }

    @Override
    public InputStream getContentByGlobalId(long globalId, Boolean canonical, Boolean dereference) {
        throw new UnsupportedOperationException("Unimplemented method 'getContentByGlobalId'");
    }

    @Override
    public InputStream getContentByHash(String contentHash, Boolean canonical) {
        this.timesGetContentByHashCalled++;
        return new ByteArrayInputStream(this.schemaContent.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String group, String name, String description, List<String> labels,
            List<String> properties, Long globalId, Long contentId, SortBy orderBy, SortOrder order, Integer offset,
            Integer limit) {
        throw new UnsupportedOperationException("Unimplemented method 'searchArtifacts'");
    }

    @Override
    public ArtifactSearchResults searchArtifactsByContent(InputStream data, SortBy orderBy, SortOrder order,
            Integer offset, Integer limit) {
        throw new UnsupportedOperationException("Unimplemented method 'searchArtifactsByContent'");
    }

    @Override
    public List<RuleType> listGlobalRules() {
        throw new UnsupportedOperationException("Unimplemented method 'listGlobalRules'");
    }

    @Override
    public void createGlobalRule(Rule data) {
        throw new UnsupportedOperationException("Unimplemented method 'createGlobalRule'");
    }

    @Override
    public void deleteAllGlobalRules() {
        throw new UnsupportedOperationException("Unimplemented method 'deleteAllGlobalRules'");
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        throw new UnsupportedOperationException("Unimplemented method 'getGlobalRuleConfig'");
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        throw new UnsupportedOperationException("Unimplemented method 'updateGlobalRuleConfig'");
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteGlobalRule'");
    }

    @Override
    public InputStream exportData() {
        throw new UnsupportedOperationException("Unimplemented method 'exportData'");
    }

    @Override
    public void importData(InputStream data) {
        throw new UnsupportedOperationException("Unimplemented method 'importData'");
    }

    @Override
    public void importData(InputStream data, boolean preserveGlobalIds, boolean preserveContentIds) {
        throw new UnsupportedOperationException("Unimplemented method 'importData'");
    }

    @Override
    public List<RoleMapping> listRoleMappings() {
        throw new UnsupportedOperationException("Unimplemented method 'listRoleMappings'");
    }

    @Override
    public void createRoleMapping(RoleMapping data) {
        throw new UnsupportedOperationException("Unimplemented method 'createRoleMapping'");
    }

    @Override
    public RoleMapping getRoleMapping(String principalId) {
        throw new UnsupportedOperationException("Unimplemented method 'getRoleMapping'");
    }

    @Override
    public void updateRoleMapping(String principalId, RoleType role) {
        throw new UnsupportedOperationException("Unimplemented method 'updateRoleMapping'");
    }

    @Override
    public void deleteRoleMapping(String principalId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteRoleMapping'");
    }

    @Override
    public UserInfo getCurrentUserInfo() {
        throw new UnsupportedOperationException("Unimplemented method 'getCurrentUserInfo'");
    }

    @Override
    public void setNextRequestHeaders(Map<String, String> requestHeaders) {
        throw new UnsupportedOperationException("Unimplemented method 'setNextRequestHeaders'");
    }

    @Override
    public Map<String, String> getHeaders() {
        throw new UnsupportedOperationException("Unimplemented method 'getHeaders'");
    }

    @Override
    public List<ConfigurationProperty> listConfigProperties() {
        throw new UnsupportedOperationException("Unimplemented method 'listConfigProperties'");
    }

    @Override
    public void setConfigProperty(String propertyName, String propertyValue) {
        throw new UnsupportedOperationException("Unimplemented method 'setConfigProperty'");
    }

    @Override
    public ConfigurationProperty getConfigProperty(String propertyName) {
        throw new UnsupportedOperationException("Unimplemented method 'getConfigProperty'");
    }

    @Override
    public void deleteConfigProperty(String propertyName) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteConfigProperty'");
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByGlobalId(long globalId) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactReferencesByGlobalId'");
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByContentId(
            long contentId) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactReferencesByContentId'");
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByContentHash(
            String contentHash) {
        return List.of();
    }

    @Override
    public List<ArtifactReference> getArtifactReferencesByCoordinates(
            String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactReferencesByCoordinates'");
    }
    
    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#addArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.NewComment)
     */
    @Override
    public Comment addArtifactVersionComment(String groupId, String artifactId, String version, NewComment comment) {
        throw new UnsupportedOperationException("Unimplemented method 'addArtifactVersionComment'");
    }
    
    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#deleteArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        throw new UnsupportedOperationException("Unimplemented method 'deleteArtifactVersionComment'");
    }

    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#editArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.NewComment)
     */
    @Override
    public void editArtifactVersionComment(String groupId, String artifactId, String version, String commentId, NewComment comment) {
        throw new UnsupportedOperationException("Unimplemented method 'editArtifactVersionComment'");
    }
    
    /**
     * @see io.apicurio.registry.rest.client.RegistryClient#getArtifactVersionComments(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public List<Comment> getArtifactVersionComments(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactVersionComments'");
    }

}
