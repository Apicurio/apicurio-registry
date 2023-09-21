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

package io.apicurio.registry.rest.v2;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.RequestAccessor;
import io.apicurio.registry.rest.v2.beans.ArtifactContent;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactOwner;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.Comment;
import io.apicurio.registry.rest.v2.beans.CreateGroupMetaData;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.GroupSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.NewComment;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.Response;

/**
 * Implements the {@link GroupsResource} JAX-RS interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class GroupsResourceImpl extends AbstractResourceImpl implements GroupsResource {
    
    @Inject
    io.apicurio.registry.rest.v3.GroupsResourceImpl v3Impl;

    @Inject
    private RequestAccessor theRequestAccessor;

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, @NotNull UpdateState data) {
        v3Impl.updateArtifactState(groupId, artifactId, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        return V2ApiUtil.fromV3(v3Impl.getArtifactVersionMetaData(groupId, artifactId, version));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, @NotNull EditableMetaData data) {
        v3Impl.updateArtifactVersionMetaData(groupId, artifactId, version, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        v3Impl.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean)
     */
    @Override
    public Response getArtifactVersion(String groupId, String artifactId, String version, Boolean dereference) {
        HandleReferencesType handleRefs = HandleReferencesType.PRESERVE;
        if (dereference != null && dereference.booleanValue()) {
            handleRefs = HandleReferencesType.DEREFERENCE;
        }
        
        return v3Impl.getArtifactVersion(groupId, artifactId, version,  handleRefs);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        v3Impl.deleteArtifactVersion(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactVersionState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateState)
     */
    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version, @NotNull UpdateState data) {
        v3Impl.updateArtifactVersionState(groupId, artifactId, version, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        return v3Impl.listArtifactRules(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public void createArtifactRule(String groupId, String artifactId, @NotNull Rule data) {
        v3Impl.createArtifactRule(groupId, artifactId, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        v3Impl.deleteArtifactRules(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        return V2ApiUtil.fromV3(v3Impl.getArtifactRuleConfig(groupId, artifactId, rule));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, @NotNull Rule data) {
        return V2ApiUtil.fromV3(v3Impl.updateArtifactRuleConfig(groupId, artifactId, rule, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        v3Impl.deleteArtifactRule(groupId, artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionReferences(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ReferenceType)
     */
    @Override
    public List<ArtifactReference> getArtifactVersionReferences(String groupId, String artifactId,
            String version, ReferenceType refType) {
        return V2ApiUtil.fromV3_ArtifactReferenceList(v3Impl.getArtifactVersionReferences(groupId, artifactId, version, refType));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getLatestArtifact(java.lang.String, java.lang.String, java.lang.Boolean)
     */
    @Override
    public Response getLatestArtifact(String groupId, String artifactId, Boolean dereference) {
        HandleReferencesType handleRefs = HandleReferencesType.PRESERVE;
        if (dereference != null && dereference.booleanValue()) {
            handleRefs = HandleReferencesType.DEREFERENCE;
        }
        return v3Impl.getLatestArtifact(groupId, artifactId, handleRefs );
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifact(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String xRegistryVersion,
            String xRegistryName, String xRegistryNameEncoded, String xRegistryDescription,
            String xRegistryDescriptionEncoded, @NotNull InputStream data) {
        requireHttpServletRequest();
        return V2ApiUtil.fromV3(v3Impl.updateArtifact(groupId, artifactId, xRegistryVersion, xRegistryName, 
                xRegistryNameEncoded, xRegistryDescription, xRegistryDescriptionEncoded, data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifact(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.ArtifactContent)
     */
    @Override
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String xRegistryVersion,
            String xRegistryName, String xRegistryNameEncoded, String xRegistryDescription,
            String xRegistryDescriptionEncoded, @NotNull ArtifactContent data) {
        requireHttpServletRequest();
        return V2ApiUtil.fromV3(v3Impl.updateArtifact(groupId, artifactId, xRegistryVersion, xRegistryName, 
                xRegistryNameEncoded, xRegistryDescription, xRegistryDescriptionEncoded, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifact(String groupId, String artifactId) {
        v3Impl.deleteArtifact(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactsInGroup(java.lang.String, java.math.BigInteger, java.math.BigInteger, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy)
     */
    @Override
    public ArtifactSearchResults listArtifactsInGroup(String groupId, BigInteger limit, BigInteger offset,
            SortOrder order, SortBy orderby) {
        return V2ApiUtil.fromV3(v3Impl.listArtifactsInGroup(groupId, limit, offset, V2ApiUtil.toV3(order), V2ApiUtil.toV3(orderby)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifact(java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.IfExists, java.lang.Boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String xRegistryArtifactType,
            String xRegistryArtifactId, String xRegistryVersion, IfExists ifExists, Boolean canonical,
            String xRegistryDescription, String xRegistryDescriptionEncoded, String xRegistryName,
            String xRegistryNameEncoded, String xRegistryContentHash, String xRegistryHashAlgorithm,
            @NotNull InputStream data) {
        requireHttpServletRequest();
        return V2ApiUtil.fromV3(v3Impl.createArtifact(groupId, xRegistryArtifactType, xRegistryArtifactId, 
                xRegistryVersion, V2ApiUtil.toV3(ifExists), canonical, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryName, 
                xRegistryNameEncoded, xRegistryContentHash, xRegistryHashAlgorithm, data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifact(java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.IfExists, java.lang.Boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.ArtifactContent)
     */
    @Override
    public ArtifactMetaData createArtifact(String groupId, String xRegistryArtifactType,
            String xRegistryArtifactId, String xRegistryVersion, IfExists ifExists, Boolean canonical,
            String xRegistryDescription, String xRegistryDescriptionEncoded, String xRegistryName,
            String xRegistryNameEncoded, String xRegistryContentHash, String xRegistryHashAlgorithm,
            @NotNull ArtifactContent data) {
        requireHttpServletRequest();
        return V2ApiUtil.fromV3(v3Impl.createArtifact(groupId, xRegistryArtifactType, xRegistryArtifactId, 
                xRegistryVersion, V2ApiUtil.toV3(ifExists), canonical, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryName, 
                xRegistryNameEncoded, xRegistryContentHash, xRegistryHashAlgorithm, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactsInGroup(java.lang.String)
     */
    @Override
    public void deleteArtifactsInGroup(String groupId) {
        v3Impl.deleteArtifactsInGroup(groupId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#testUpdateArtifact(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public void testUpdateArtifact(String groupId, String artifactId, @NotNull InputStream data) {
        v3Impl.testUpdateArtifact(groupId, artifactId, data);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactVersions(java.lang.String, java.lang.String, java.math.BigInteger, java.math.BigInteger)
     */
    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, BigInteger offset, BigInteger limit) {
        return V2ApiUtil.fromV3(v3Impl.listArtifactVersions(groupId, artifactId, offset, limit));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion,
            String xRegistryName, String xRegistryDescription, String xRegistryDescriptionEncoded,
            String xRegistryNameEncoded, @NotNull InputStream data) {
        return V2ApiUtil.fromV3(v3Impl.createArtifactVersion(groupId, artifactId, xRegistryVersion, xRegistryName, 
                xRegistryDescription, xRegistryDescriptionEncoded, xRegistryNameEncoded, data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.ArtifactContent)
     */
    @Override
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion,
            String xRegistryName, String xRegistryDescription, String xRegistryDescriptionEncoded,
            String xRegistryNameEncoded, @NotNull ArtifactContent data) {
        return V2ApiUtil.fromV3(v3Impl.createArtifactVersion(groupId, artifactId, xRegistryVersion, xRegistryName, 
                xRegistryDescription, xRegistryDescriptionEncoded, xRegistryNameEncoded, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactOwner(java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactOwner getArtifactOwner(String groupId, String artifactId) {
        return V2ApiUtil.fromV3(v3Impl.getArtifactOwner(groupId, artifactId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactOwner(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.ArtifactOwner)
     */
    @Override
    public void updateArtifactOwner(String groupId, String artifactId, @NotNull ArtifactOwner data) {
        v3Impl.updateArtifactOwner(groupId, artifactId, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getGroupById(java.lang.String)
     */
    @Override
    public GroupMetaData getGroupById(String groupId) {
        return V2ApiUtil.fromV3(v3Impl.getGroupById(groupId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteGroupById(java.lang.String)
     */
    @Override
    public void deleteGroupById(String groupId) {
        v3Impl.deleteGroupById(groupId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listGroups(java.math.BigInteger, java.math.BigInteger, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy)
     */
    @Override
    public GroupSearchResults listGroups(BigInteger limit, BigInteger offset, SortOrder order, SortBy orderby) {
        return V2ApiUtil.fromV3(v3Impl.listGroups(limit, offset, V2ApiUtil.toV3(order), V2ApiUtil.toV3(orderby)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createGroup(io.apicurio.registry.rest.v2.beans.CreateGroupMetaData)
     */
    @Override
    public GroupMetaData createGroup(@NotNull CreateGroupMetaData data) {
        return V2ApiUtil.fromV3(v3Impl.createGroup(V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        return V2ApiUtil.fromV3(v3Impl.getArtifactMetaData(groupId, artifactId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, @NotNull EditableMetaData data) {
        v3Impl.updateArtifactMetaData(groupId, artifactId, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            Boolean canonical, @NotNull InputStream data) {
        return V2ApiUtil.fromV3(v3Impl.getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.lang.Boolean, io.apicurio.registry.rest.v2.beans.ArtifactContent)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            Boolean canonical, @NotNull ArtifactContent data) {
        return V2ApiUtil.fromV3(v3Impl.getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionComments(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public List<Comment> getArtifactVersionComments(String groupId, String artifactId, String version) {
        return V2ApiUtil.fromV3_CommentList(v3Impl.getArtifactVersionComments(groupId, artifactId, version));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#addArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.NewComment)
     */
    @Override
    public Comment addArtifactVersionComment(String groupId, String artifactId, String version,
            @NotNull NewComment data) {
        return V2ApiUtil.fromV3(v3Impl.addArtifactVersionComment(groupId, artifactId, version, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.NewComment)
     */
    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, @NotNull NewComment data) {
        v3Impl.updateArtifactVersionComment(groupId, artifactId, version, commentId, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        v3Impl.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }

}
