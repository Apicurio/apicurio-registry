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

import com.google.common.hash.Hashing;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParametersConflictException;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactOwner;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.ContentCreateRequest;
import io.apicurio.registry.rest.v2.beans.CreateGroupMetaData;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.GroupMetaData;
import io.apicurio.registry.rest.v2.beans.GroupSearchResults;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactIdException;
import io.apicurio.registry.storage.InvalidGroupIdException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.utils.ArtifactIdValidator;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.JAXRSClientUtil;
import io.quarkus.security.identity.SecurityIdentity;
import org.jose4j.base64url.Base64;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_CANONICAL;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_DESCRIPTION;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_DESCRIPTION_ENCODED;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_EDITABLE_METADATA;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_FROM_URL;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_GROUP_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_IF_EXISTS;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_NAME;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_NAME_ENCODED;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_SHA;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_UPDATE_STATE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_OWNER;

/**
 * Implements the {@link GroupsResource} JAX-RS interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class GroupsResourceImpl implements GroupsResource {

    private static final String EMPTY_CONTENT_ERROR_MESSAGE = "Empty content is not allowed.";
    private static final Integer GET_GROUPS_LIMIT = 1000;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    ArtifactIdGenerator idGenerator;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Context
    HttpServletRequest request;

    @Inject
    RestConfig restConfig;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getLatestArtifact(java.lang.String, java.lang.String, java.lang.Boolean)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public Response getLatestArtifact(String groupId, String artifactId, Boolean dereference) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (dereference == null) {
            dereference = Boolean.FALSE;
        }

        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(gidOrNull(groupId), artifactId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
        StoredArtifactDto artifact = storage.getArtifact(gidOrNull(groupId), artifactId);

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        ContentHandle contentToReturn = artifact.getContent();

        //TODO:carnalca when dereferencing is implemented, we should return the content dereferenced here
        /*
        if (dereference && !artifact.getReferences().isEmpty()) {
            contentToReturn = factory.getArtifactTypeProvider(metaData.getType()).getContentDereferencer().dereference(artifact.getContent(), storage.resolveReferences(artifact.getReferences()));
        }
        */
        Response.ResponseBuilder builder = Response.ok(contentToReturn, contentType);
        checkIfDeprecated(metaData::getState, groupId, artifactId, metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifact(String, String, String, String, String, String, String, InputStream)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME, "4", KEY_NAME_ENCODED, "5", KEY_DESCRIPTION, "6", KEY_DESCRIPTION_ENCODED})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String xRegistryVersion,
                                           String xRegistryName, String xRegistryNameEncoded, String xRegistryDescription,
                                           String xRegistryDescriptionEncoded, InputStream data) {
        return this.updateArtifactWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryNameEncoded, xRegistryDescription, xRegistryDescriptionEncoded, data, Collections.emptyList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifact(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.ContentCreateRequest)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME, "4", KEY_NAME_ENCODED, "5", KEY_DESCRIPTION, "6", KEY_DESCRIPTION_ENCODED})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String xRegistryVersion,
                                           String xRegistryName, String xRegistryNameEncoded, String xRegistryDescription,
                                           String xRegistryDescriptionEncoded, ContentCreateRequest data) {
        requireParameter("content", data.getContent());
        return this.updateArtifactWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryNameEncoded, xRegistryDescription, xRegistryDescriptionEncoded, IoUtil.toStream(data.getContent()), data.getReferences());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionReferences(String, String, String)
     **/
    @Override
    public List<ArtifactReference> getArtifactVersionReferences(String groupId, String artifactId, String version) {
        if (null == version) {
            return storage.getArtifact(gidOrNull(groupId), artifactId).getReferences().stream()
                    .map(V2ApiUtil::referenceDtoToReference)
                    .collect(Collectors.toList());
        } else {
            return storage.getArtifactVersion(gidOrNull(groupId), artifactId, version).getReferences().stream()
                    .map(V2ApiUtil::referenceDtoToReference)
                    .collect(Collectors.toList());
        }
    }

    private ArtifactMetaData updateArtifactWithRefs(String groupId, String artifactId, String xRegistryVersion, String xRegistryName, String xRegistryNameEncoded, String xRegistryDescription, String xRegistryDescriptionEncoded, InputStream data, List<ArtifactReference> references) {

        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        maxOneOf("X-Registry-Name", xRegistryName, "X-Registry-Name-Encoded", xRegistryNameEncoded);
        maxOneOf("X-Registry-Description", xRegistryDescription, "X-Registry-Description-Encoded", xRegistryDescriptionEncoded);

        String artifactName = getOneOf(xRegistryName, decode(xRegistryNameEncoded));
        String artifactDescription = getOneOf(xRegistryDescription, decode(xRegistryDescriptionEncoded));

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        return updateArtifactInternal(groupId, artifactId, xRegistryVersion, artifactName, artifactDescription, content, getContentType(), references);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void deleteArtifact(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifact(gidOrNull(groupId), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(gidOrNull(groupId), artifactId);
        return V2ApiUtil.dtoToMetaData(gidOrNull(groupId), artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.EditableMetaData)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_EDITABLE_METADATA})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (data.getProperties() != null) {
            data.getProperties().forEach((k,v) -> requireParameter("property value", v));
        }

        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setProperties(data.getProperties());
        storage.updateArtifactMetaData(gidOrNull(groupId), artifactId, dto);
    }
    
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public ArtifactOwner getArtifactOwner(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(gidOrNull(groupId), artifactId);
        ArtifactOwner owner = new ArtifactOwner();
        owner.setOwner(dto.getCreatedBy());
        return owner;
    }

    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_OWNER})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.AdminOrOwner)
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwner data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactOwnerDto dto = new ArtifactOwnerDto(data.getOwner());
        storage.updateArtifactOwner(gidOrNull(groupId), artifactId, dto);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public GroupMetaData getGroupById(String groupId) {
        GroupMetaDataDto group = storage.getGroupMetaData(groupId);
        return V2ApiUtil.groupDtoToGroup(group);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GroupOnly, level=AuthorizedLevel.Write)
    public void deleteGroupById(String groupId) {
        storage.deleteGroup(groupId);
    }

    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public GroupSearchResults listGroups(Integer limit, Integer offset, SortOrder order, SortBy orderby) {
        if (orderby == null) {
            orderby = SortBy.name;
        }
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

        Set<SearchFilter> filters = Collections.emptySet();

        GroupSearchResultsDto resultsDto = storage.searchGroups(filters, oBy, oDir, offset, limit);
        return V2ApiUtil.dtoToSearchResults(resultsDto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public GroupMetaData createGroup(CreateGroupMetaData data) {
        GroupMetaDataDto.GroupMetaDataDtoBuilder group = GroupMetaDataDto.builder()
                .groupId(data.getId())
                .description(data.getDescription())
                .properties(data.getProperties());

        String user = securityIdentity.getPrincipal().getName();
        group.createdBy(user).createdOn(new Date().getTime());

        storage.createGroup(group.build());

        return V2ApiUtil.groupDtoToGroup(storage.getGroupMetaData(data.getId()));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (canonical == null) {
            canonical = Boolean.FALSE;
        }
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        if (ContentTypeUtil.isApplicationYaml(getContentType())) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(gidOrNull(groupId), artifactId, canonical, content);
        return V2ApiUtil.dtoToVersionMetaData(gidOrNull(groupId), artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        return storage.getArtifactRules(gidOrNull(groupId), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());

        if (!storage.isArtifactExists(gidOrNull(groupId), artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        storage.createArtifactRule(gidOrNull(groupId), artifactId, data.getType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void deleteArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifactRules(gidOrNull(groupId), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

        RuleConfigurationDto dto = storage.getArtifactRule(gidOrNull(groupId), artifactId, rule);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setType(rule);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE_TYPE, "3", KEY_RULE})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateArtifactRule(gidOrNull(groupId), artifactId, rule, dto);
        Rule rval = new Rule();
        rval.setType(rule);
        rval.setConfig(data.getConfig());
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE_TYPE})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

        storage.deleteArtifactRule(gidOrNull(groupId), artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateState)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_UPDATE_STATE})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("body.state", data.getState());
        storage.updateArtifactState(gidOrNull(groupId), artifactId, data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#testUpdateArtifact(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.UPDATE, Collections.emptyMap()); //TODO:references not supported for testing update
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public Response getArtifactVersion(String groupId, String artifactId, String version, Boolean dereference) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        if (dereference == null) {
            dereference = Boolean.FALSE;
        }

        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(gidOrNull(groupId), artifactId, version);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        }
        StoredArtifactDto artifact = storage.getArtifactVersion(gidOrNull(groupId), artifactId, version);

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        ContentHandle contentToReturn = artifact.getContent();
        //TODO:carnalca when dereferencing is implemented, we should return the content dereferenced here
        /*
        if (dereference && !artifact.getReferences().isEmpty()) {
            contentToReturn = factory.getArtifactTypeProvider(metaData.getType()).getContentDereferencer().dereference(artifact.getContent(), storage.resolveReferences(artifact.getReferences()));
        }
        */

        Response.ResponseBuilder builder = Response.ok(contentToReturn, contentType);
        checkIfDeprecated(metaData::getState, groupId, artifactId, version, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(gidOrNull(groupId), artifactId, version);
        return V2ApiUtil.dtoToVersionMetaData(gidOrNull(groupId), artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.EditableMetaData)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_EDITABLE_METADATA})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);
        if (data.getProperties() != null) {
            data.getProperties().forEach((k,v) -> requireParameter("property value", v));
        }
        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setProperties(data.getProperties());
        storage.updateArtifactVersionMetaData(gidOrNull(groupId), artifactId, version, dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        storage.deleteArtifactVersionMetaData(gidOrNull(groupId), artifactId, version);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifactVersionState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateState)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_UPDATE_STATE})
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Write)
    public void updateArtifactVersionState(String groupId, String artifactId, String version, UpdateState data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        storage.updateArtifactState(gidOrNull(groupId), artifactId, version, data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactsInGroup(java.lang.String, java.lang.Integer, java.lang.Integer, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupOnly, level=AuthorizedLevel.Read)
    public ArtifactSearchResults listArtifactsInGroup(String groupId, Integer limit, Integer offset,
            SortOrder order, SortBy orderby) {
        requireParameter("groupId", groupId);

        if (orderby == null) {
            orderby = SortBy.name;
        }
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroup(gidOrNull(groupId)));

        ArtifactSearchResultsDto resultsDto = storage.searchArtifacts(filters, oBy, oDir, offset, limit);
        return V2ApiUtil.dtoToSearchResults(resultsDto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactsInGroup(java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID})
    @Authorized(style=AuthorizedStyle.GroupOnly, level=AuthorizedLevel.Write)
    public void deleteArtifactsInGroup(String groupId) {
        requireParameter("groupId", groupId);

        storage.deleteArtifacts(gidOrNull(groupId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifact(String, ArtifactType, String, String, IfExists, Boolean, String, String, String, String, String, String, InputStream)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_TYPE, "2", KEY_ARTIFACT_ID, "3", KEY_VERSION, "4", KEY_IF_EXISTS, "5", KEY_CANONICAL, "6", KEY_DESCRIPTION, "7", KEY_DESCRIPTION_ENCODED, "8", KEY_NAME, "9", KEY_NAME_ENCODED, "10", KEY_FROM_URL, "11", KEY_SHA})
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public ArtifactMetaData createArtifact(String groupId, String xRegistryArtifactType, String xRegistryArtifactId,
                                           String xRegistryVersion, IfExists ifExists, Boolean canonical,
                                           String xRegistryDescription, String xRegistryDescriptionEncoded,
                                           String xRegistryName, String xRegistryNameEncoded,
                                           String xRegistryContentHash, String xRegistryHashAlgorithm, InputStream data) {
        return this.createArtifactWithRefs(groupId, xRegistryArtifactType, xRegistryArtifactId, xRegistryVersion, ifExists, canonical, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryName, xRegistryNameEncoded, xRegistryContentHash, xRegistryHashAlgorithm, data, Collections.emptyList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifact(String, ArtifactType, String, String, IfExists, Boolean, String, String, String, String, String, String, ContentCreateRequest)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_TYPE, "2", KEY_ARTIFACT_ID, "3", KEY_VERSION, "4", KEY_IF_EXISTS, "5", KEY_CANONICAL, "6", KEY_DESCRIPTION, "7", KEY_DESCRIPTION_ENCODED, "8", KEY_NAME, "9", KEY_NAME_ENCODED, "10", "from_url" /*KEY_FROM_URL*/, "11", "artifact_sha" /*KEY_SHA*/})
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public ArtifactMetaData createArtifact(String groupId, String xRegistryArtifactType, String xRegistryArtifactId,
                                           String xRegistryVersion, IfExists ifExists, Boolean canonical,
                                           String xRegistryDescription, String xRegistryDescriptionEncoded,
                                           String xRegistryName, String xRegistryNameEncoded,
                                           String xRegistryContentHash, String xRegistryHashAlgorithm, ContentCreateRequest data) {
        requireParameter("content", data.getContent());

        Client client = null;
        InputStream content;
        try {
            try {
                URL url = new URL(data.getContent());
                client = JAXRSClientUtil.getJAXRSClient(restConfig.getDownloadSkipSSLValidation());
                content = fetchContentFromURL(client, url.toURI());
            } catch (MalformedURLException | URISyntaxException e) {
                content = IoUtil.toStream(data.getContent());
            }

            return this.createArtifactWithRefs(groupId, xRegistryArtifactType, xRegistryArtifactId, xRegistryVersion, ifExists, canonical, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryName, xRegistryNameEncoded, xRegistryContentHash, xRegistryHashAlgorithm, content, data.getReferences());
        } catch (KeyManagementException kme) {
            throw new RuntimeException(kme);
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public enum RegistryHashAlgorithm {
        SHA256,
        MD5
    }

    /**
     * Return an InputStream for the resource to be downloaded
     * @param url
     */
    private InputStream fetchContentFromURL(Client client, URI url) {
        try {
            // 1. Registry issues HTTP HEAD request to the target URL.
            List<Object> contentLengthHeaders = client
                    .target(url)
                    .request()
                    .head()
                    .getHeaders()
                    .get("Content-Length");

            if (contentLengthHeaders == null || contentLengthHeaders.size() < 1) {
                throw new BadRequestException("Requested resource URL does not provide 'Content-Length' in the headers");
            }

            // 2. According to HTTP specification, target server must return Content-Length header.
            int contentLength = Integer.parseInt(contentLengthHeaders.get(0).toString());

            // 3. Registry analyzes value of Content-Length to check if file with declared size could be processed securely.
            if (contentLength > restConfig.getDownloadMaxSize()) {
                throw new BadRequestException("Requested resource is bigger than " + restConfig.getDownloadMaxSize() + " and cannot be downloaded.");
            }

            if (contentLength <= 0) {
                throw new BadRequestException("Requested resource URL is providing 'Content-Length' <= 0.");
            }

            // 4. Finally, registry issues HTTP GET to the target URL and fetches only amount of bytes specified by HTTP HEAD from step 1.
            return new BufferedInputStream(client
                    .target(url)
                    .request()
                    .get()
                    .readEntity(InputStream.class), contentLength);
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            throw new BadRequestException("Errors downloading the artifact content.", e);
        }
    }

    /**
     * Creates an artifact with references.  Shared by both variants of createArtifact.
     * @param groupId
     * @param xRegistryArtifactType
     * @param xRegistryArtifactId
     * @param xRegistryVersion
     * @param ifExists
     * @param canonical
     * @param xRegistryDescription
     * @param xRegistryDescriptionEncoded
     * @param xRegistryName
     * @param xRegistryNameEncoded
     * @param xRegistryContentHash
     * @param xRegistryHashAlgorithm
     * @param data
     * @param references
     */
    private ArtifactMetaData createArtifactWithRefs(String groupId, String xRegistryArtifactType, String xRegistryArtifactId,
                                                    String xRegistryVersion, IfExists ifExists, Boolean canonical,
                                                    String xRegistryDescription, String xRegistryDescriptionEncoded,
                                                    String xRegistryName, String xRegistryNameEncoded,
                                                    String xRegistryContentHash, String xRegistryHashAlgorithm,
                                                    InputStream data, List<ArtifactReference> references) {

        requireParameter("groupId", groupId);

        maxOneOf("X-Registry-Name", xRegistryName, "X-Registry-Name-Encoded", xRegistryNameEncoded);
        maxOneOf("X-Registry-Description", xRegistryDescription, "X-Registry-Description-Encoded", xRegistryDescriptionEncoded);

        String artifactName = getOneOf(xRegistryName, decode(xRegistryNameEncoded));
        String artifactDescription = getOneOf(xRegistryDescription, decode(xRegistryDescriptionEncoded));

        if (!ArtifactIdValidator.isGroupIdAllowed(groupId)) {
            throw new InvalidGroupIdException(ArtifactIdValidator.GROUP_ID_ERROR_MESSAGE);
        }

        // TODO do something with the optional user-provided Version

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        // Mitigation for MITM attacks, verify that the artifact is the expected one
        if (xRegistryContentHash != null) {
            String calculatedSha = null;
            try {
                RegistryHashAlgorithm algorithm = (xRegistryHashAlgorithm == null) ? RegistryHashAlgorithm.SHA256 : RegistryHashAlgorithm.valueOf(xRegistryHashAlgorithm);
                switch (algorithm) {
                    case MD5:
                        calculatedSha = Hashing.md5().hashString(content.content(), StandardCharsets.UTF_8).toString();
                        break;
                    case SHA256:
                        calculatedSha = Hashing.sha256().hashString(content.content(), StandardCharsets.UTF_8).toString();
                        break;
                }
            } catch (Exception e) {
                throw new BadRequestException("Requested hash algorithm not supported");
            }

            if (!calculatedSha.equals(xRegistryContentHash.trim())) {
                throw new BadRequestException("Provided Artifact Hash doesn't match with the content");
            }
        }

        final boolean fcanonical = canonical == null ? Boolean.FALSE : canonical;

        String ct = getContentType();
        try {
            String artifactId = xRegistryArtifactId;

            if (artifactId == null || artifactId.trim().isEmpty()) {
                artifactId = idGenerator.generate();
            } else if (!ArtifactIdValidator.isArtifactIdAllowed(artifactId)) {
                throw new InvalidArtifactIdException(ArtifactIdValidator.ARTIFACT_ID_ERROR_MESSAGE);
            }
            if (ContentTypeUtil.isApplicationYaml(ct) ||
                    (ContentTypeUtil.isApplicationCreateExtended(ct) && ContentTypeUtil.isParsableYaml(content))) {
                content = ContentTypeUtil.yamlToJson(content);
            }

            String artifactType = ArtifactTypeUtil.determineArtifactType(content, xRegistryArtifactType, ct, factory.getAllArtifactTypes());

            final List<ArtifactReferenceDto> referencesAsDtos = references.stream()
                    .map(V2ApiUtil::referenceToDto)
                    .collect(Collectors.toList());

            //Try to resolve the new artifact references and the nested ones (if any)
            final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(referencesAsDtos);

            rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.CREATE, resolvedReferences);

            final String finalArtifactId = artifactId;
            EditableArtifactMetaDataDto metaData = getEditableMetaData(artifactName, artifactDescription);
            ArtifactMetaDataDto amd = storage.createArtifactWithMetadata(gidOrNull(groupId), artifactId, xRegistryVersion, artifactType, content, metaData, referencesAsDtos);
            return V2ApiUtil.dtoToMetaData(gidOrNull(groupId), finalArtifactId, artifactType, amd);
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(groupId, xRegistryArtifactId, xRegistryVersion, ifExists, content, ct, fcanonical, references);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactVersions(java.lang.String, java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    @Authorized(style=AuthorizedStyle.GroupAndArtifact, level=AuthorizedLevel.Read)
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset, Integer limit) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }

        VersionSearchResultsDto resultsDto = storage.searchVersions(gidOrNull(groupId), artifactId, offset, limit);
        return V2ApiUtil.dtoToSearchResults(resultsDto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactVersion(String, String, String, String, String, String, String, InputStream)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME, "4", KEY_DESCRIPTION, "5", KEY_DESCRIPTION_ENCODED, "6", KEY_NAME_ENCODED})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public VersionMetaData createArtifactVersion(String groupId, String artifactId,
                                                 String xRegistryVersion, String xRegistryName,
                                                 String xRegistryDescription, String xRegistryDescriptionEncoded,
                                                 String xRegistryNameEncoded, InputStream data) {
        return this.createArtifactVersionWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryNameEncoded, data, Collections.emptyList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.ContentCreateRequest)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME, "4", KEY_DESCRIPTION, "5", KEY_DESCRIPTION_ENCODED, "6", KEY_NAME_ENCODED})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion,
                                                 String xRegistryName, String xRegistryDescription, String xRegistryDescriptionEncoded,
                                                 String xRegistryNameEncoded, ContentCreateRequest data) {
        requireParameter("content", data.getContent());
        return this.createArtifactVersionWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryNameEncoded, IoUtil.toStream(data.getContent()), data.getReferences());
    }

    /**
     * Creates an artifact version with references.  Shared implementation for both variants of createArtifactVersion.
     * @param groupId
     * @param artifactId
     * @param xRegistryVersion
     * @param xRegistryName
     * @param xRegistryDescription
     * @param xRegistryDescriptionEncoded
     * @param xRegistryNameEncoded
     * @param data
     * @param references
     */
    private VersionMetaData createArtifactVersionWithRefs(String groupId, String artifactId, String xRegistryVersion, String xRegistryName, String xRegistryDescription, String xRegistryDescriptionEncoded, String xRegistryNameEncoded, InputStream data, List<ArtifactReference> references) {
        // TODO do something with the user-provided version info
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        maxOneOf("X-Registry-Name", xRegistryName, "X-Registry-Name-Encoded", xRegistryNameEncoded);
        maxOneOf("X-Registry-Description", xRegistryDescription, "X-Registry-Description-Encoded", xRegistryDescriptionEncoded);

        String artifactName = getOneOf(xRegistryName, decode(xRegistryNameEncoded));
        String artifactDescription = getOneOf(xRegistryDescription, decode(xRegistryDescriptionEncoded));

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        //Transform the given references into dtos and set the contentId, this will also detect if any of the passed references does not exist.
        final List<ArtifactReferenceDto> referencesAsDtos = references.stream()
                .map(V2ApiUtil::referenceToDto)
                .collect(Collectors.toList());

        //Try to resolve the new artifact references and the nested ones (if any)
        final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(referencesAsDtos);

        String artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.UPDATE, resolvedReferences);
        EditableArtifactMetaDataDto metaData = getEditableMetaData(artifactName, artifactDescription);
        ArtifactMetaDataDto amd = storage.updateArtifactWithMetadata(gidOrNull(groupId), artifactId, xRegistryVersion, artifactType, content, metaData, referencesAsDtos);
        return V2ApiUtil.dtoToVersionMetaData(gidOrNull(groupId), artifactId, artifactType, amd);
    }

    /**
     * Check to see if the artifact version is deprecated.
     *
     * @param stateSupplier
     * @param groupId
     * @param artifactId
     * @param version
     * @param builder
     */
    private void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String groupId, String artifactId, String version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, groupId, artifactId, version, builder);
    }

    /**
     * Looks up the artifact type for the given artifact.
     * @param groupId
     * @param artifactId
     */
    private String lookupArtifactType(String groupId, String artifactId) {
        return storage.getArtifactMetaData(gidOrNull(groupId), artifactId).getType();
    }

    /**
     * Make sure this is ONLY used when request instance is active.
     * e.g. in actual http request
     */
    private String getContentType() {
        return request.getContentType();
    }

    private static final void requireParameter(String parameterName, Object parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
    }

    private static void maxOneOf(String parameterOneName, Object parameterOneValue, String parameterTwoName, Object parameterTwoValue) {
        if (parameterOneValue != null && parameterTwoValue != null) {
            throw new ParametersConflictException(parameterOneName, parameterTwoName);
        }
    }

    private static <T> T getOneOf(T parameterOneValue, T parameterTwoValue) {
        return parameterOneValue != null ? parameterOneValue : parameterTwoValue;
    }

    private static String decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        return new String(Base64.decode(encoded));
    }

    private ArtifactMetaData handleIfExists(String groupId, String artifactId, String version,
            IfExists ifExists, ContentHandle content, String contentType, boolean canonical, List<ArtifactReference> references) {
        final ArtifactMetaData artifactMetaData = getArtifactMetaData(groupId, artifactId);
        if (ifExists == null) {
            ifExists = IfExists.FAIL;
        }

        switch (ifExists) {
            case UPDATE:
                return updateArtifactInternal(groupId, artifactId, version, content, contentType, references);
            case RETURN:
                return artifactMetaData;
            case RETURN_OR_UPDATE:
                return handleIfExistsReturnOrUpdate(groupId, artifactId, version, content, contentType, canonical, references);
            default:
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }
    }

    private ArtifactMetaData handleIfExistsReturnOrUpdate(String groupId, String artifactId, String version,
            ContentHandle content, String contentType, boolean canonical, List<ArtifactReference> references) {
        try {
            ArtifactVersionMetaDataDto mdDto = this.storage.getArtifactVersionMetaData(gidOrNull(groupId), artifactId, canonical, content);
            ArtifactMetaData md = V2ApiUtil.dtoToMetaData(gidOrNull(groupId), artifactId, null, mdDto);
            return md;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - we'll update the artifact if there is no matching content already there.
        }
        return updateArtifactInternal(groupId, artifactId, version, content, contentType, references);
    }

    private ArtifactMetaData updateArtifactInternal(String groupId, String artifactId, String version,
                                                    ContentHandle content, String contentType, List<ArtifactReference> references) {
        return this.updateArtifactInternal(groupId, artifactId, version, null, null, content, contentType, references);
    }

    private ArtifactMetaData updateArtifactInternal(String groupId, String artifactId, String version,
                                                    String name, String description,
                                                    ContentHandle content, String contentType, List<ArtifactReference> references) {

        if (ContentTypeUtil.isApplicationYaml(contentType)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = lookupArtifactType(groupId, artifactId);

        //Transform the given references into dtos and set the contentId, this will also detect if any of the passed references does not exist.
        final List<ArtifactReferenceDto> referencesAsDtos = references.stream()
                .map(V2ApiUtil::referenceToDto)
                .collect(Collectors.toList());

        final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(referencesAsDtos);

        rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.UPDATE, resolvedReferences);
        EditableArtifactMetaDataDto metaData = getEditableMetaData(name, description);
        ArtifactMetaDataDto dto = storage.updateArtifactWithMetadata(gidOrNull(groupId), artifactId, version, artifactType, content, metaData, referencesAsDtos);
        return V2ApiUtil.dtoToMetaData(gidOrNull(groupId), artifactId, artifactType, dto);
    }

    private EditableArtifactMetaDataDto getEditableMetaData(String name, String description) {
        if (name != null || description != null) {
            return new EditableArtifactMetaDataDto(name, description, null, null);
        }
        return null;
    }

    private String gidOrNull(String groupId) {
        if ("default".equalsIgnoreCase(groupId)) {
            return null;
        }
        return groupId;
    }
}
