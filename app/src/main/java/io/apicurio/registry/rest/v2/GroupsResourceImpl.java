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

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
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
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.utils.ArtifactIdValidator;

/**
 * Implements the {@link GroupsResource} JAX-RS interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@RestMetricsResponseFilteredNameBinding
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
public class GroupsResourceImpl implements GroupsResource {

    private static final String EMPTY_CONTENT_ERROR_MESSAGE = "Empty content is not allowed.";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    ArtifactIdGenerator idGenerator;

    @Context
    HttpServletRequest request;

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getLatestArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public Response getLatestArtifact(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(gidOrNull(groupId), artifactId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
        StoredArtifactDto artifact = storage.getArtifact(gidOrNull(groupId), artifactId);

        // The content-type will be different for protobuf artifacts, graphql artifacts, and XML artifacts
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (metaData.getType() == ArtifactType.PROTOBUF) {
            contentType = ArtifactMediaTypes.PROTO;
        }
        if (metaData.getType() == ArtifactType.GRAPHQL) {
            contentType = ArtifactMediaTypes.GRAPHQL;
        }
        if (metaData.getType() == ArtifactType.WSDL || metaData.getType() == ArtifactType.XSD || metaData.getType() == ArtifactType.XML) {
            contentType = ArtifactMediaTypes.XML;
        }

        Response.ResponseBuilder builder = Response.ok(artifact.getContent(), contentType);
        checkIfDeprecated(metaData::getState, groupId, artifactId, metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#updateArtifact(java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    @Authorized
    public CompletionStage<ArtifactMetaData> updateArtifact(String groupId, String artifactId, String xRegistryVersion, InputStream data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        return updateArtifactInternal(groupId, artifactId, xRegistryVersion, content, getContentType());
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized
    public void deleteArtifact(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifact(gidOrNull(groupId), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
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
    @Authorized
    public void updateArtifactMetaData(String groupId, String artifactId, EditableMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setProperties(data.getProperties());
        storage.updateArtifactMetaData(gidOrNull(groupId), artifactId, dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
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
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        return storage.getArtifactRules(gidOrNull(groupId), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Authorized
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());
        storage.createArtifactRule(gidOrNull(groupId), artifactId, data.getType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized
    public void deleteArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifactRules(gidOrNull(groupId), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
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
    @Authorized
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
    @Authorized
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
    @Authorized
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

        ArtifactType artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.UPDATE);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public Response getArtifactVersion(String groupId, String artifactId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(gidOrNull(groupId), artifactId, version);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        }
        StoredArtifactDto artifact = storage.getArtifactVersion(gidOrNull(groupId), artifactId, version);

        // The content-type will be different for protobuf artifacts, graphql artifacts, and XML artifacts
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (metaData.getType() == ArtifactType.PROTOBUF) {
            contentType = ArtifactMediaTypes.PROTO;
        }
        if (metaData.getType() == ArtifactType.GRAPHQL) {
            contentType = ArtifactMediaTypes.GRAPHQL;
        }
        if (metaData.getType() == ArtifactType.WSDL || metaData.getType() == ArtifactType.XSD || metaData.getType() == ArtifactType.XML) {
            contentType = ArtifactMediaTypes.XML;
        }

        Response.ResponseBuilder builder = Response.ok(artifact.getContent(), contentType);
        checkIfDeprecated(metaData::getState, groupId, artifactId, version, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
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
    @Authorized
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

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
    @Authorized
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
    @Authorized
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
        filters.add(new SearchFilter(SearchFilterType.group, gidOrNull(groupId)));

        ArtifactSearchResultsDto resultsDto = storage.searchArtifacts(filters, oBy, oDir, offset, limit);
        return V2ApiUtil.dtoToSearchResults(resultsDto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#deleteArtifactsInGroup(java.lang.String)
     */
    @Override
    @Authorized(AuthorizedStyle.GroupOnly)
    public void deleteArtifactsInGroup(String groupId) {
        requireParameter("groupId", groupId);

        storage.deleteArtifacts(gidOrNull(groupId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, java.lang.String, java.lang.String, io.apicurio.registry.rest.v2.beans.IfExists, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(String groupId,
            ArtifactType xRegistryArtifactType, String xRegistryArtifactId, String xRegistryVersion,
            IfExists ifExists, Boolean canonical, InputStream data) {
        requireParameter("groupId", groupId);
        if (!ArtifactIdValidator.isGroupIdAllowed(groupId)) {
            throw new InvalidGroupIdException(ArtifactIdValidator.GROUP_ID_ERROR_MESSAGE);
        }

        // TODO do something with the optional user-provided Version

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        final boolean fcanonical = canonical == null ? Boolean.FALSE : canonical;

        String ct = getContentType();
        final ContentHandle finalContent = content;
        try {
            String artifactId = xRegistryArtifactId;

            if (artifactId == null || artifactId.trim().isEmpty()) {
                artifactId = idGenerator.generate();
            } else if (!ArtifactIdValidator.isArtifactIdAllowed(artifactId)) {
                throw new InvalidArtifactIdException(ArtifactIdValidator.ARTIFACT_ID_ERROR_MESSAGE);
            }
            if (ContentTypeUtil.isApplicationYaml(ct)) {
                content = ContentTypeUtil.yamlToJson(content);
            }

            ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, ct);
            rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.CREATE);
            final String finalArtifactId = artifactId;
            return storage.createArtifact(gidOrNull(groupId), artifactId, xRegistryVersion, artifactType, content)
                    .exceptionally(t -> {
                        if (t instanceof CompletionException) {
                            t = t.getCause();
                        }
                        if (t instanceof ArtifactAlreadyExistsException) {
                            return null;
                        }
                        throw new CompletionException(t);
                    })
                    .thenCompose(amd -> amd == null ?
                            handleIfExists(groupId, xRegistryArtifactId, xRegistryVersion, ifExists, finalContent, ct, fcanonical) :
                            CompletableFuture.completedFuture(V2ApiUtil.dtoToMetaData(gidOrNull(groupId), finalArtifactId, artifactType, amd))
                    );
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(groupId, xRegistryArtifactId, xRegistryVersion, ifExists, content, ct, fcanonical);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v2.GroupsResource#listArtifactVersions(java.lang.String, java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, Integer offset,
            Integer limit) {
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
     * @see io.apicurio.registry.rest.v2.GroupsResource#createArtifactVersion(java.lang.String, java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    @Authorized
    public CompletionStage<VersionMetaData> createArtifactVersion(String groupId, String artifactId,
            String xRegistryVersion, InputStream data) {
        // TODO do something with the user-provided version info

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

        ArtifactType artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(gidOrNull(groupId), artifactId, xRegistryVersion, artifactType, content)
                .thenApply(amd -> V2ApiUtil.dtoToVersionMetaData(gidOrNull(groupId), artifactId, artifactType, amd));
    }

    /**
     * Check to see if the artifact version is deprecated.
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
    private ArtifactType lookupArtifactType(String groupId, String artifactId) {
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

    /**
     * Figures out the artifact type in the following order of precedent:
     * <p>
     * 1) The provided X-Registry-ArtifactType header
     * 2) A hint provided in the Content-Type header
     * 3) Determined from the content itself
     *
     * @param content       the content
     * @param xArtifactType the artifact type
     * @param ct            content type from request API
     */
    private static ArtifactType determineArtifactType(ContentHandle content, ArtifactType xArtifactType, String ct) {
        ArtifactType artifactType = xArtifactType;
        if (artifactType == null) {
            artifactType = getArtifactTypeFromContentType(ct);
            if (artifactType == null) {
                artifactType = ArtifactTypeUtil.discoverType(content, ct);
            }
        }
        return artifactType;
    }

    /**
     * Tries to figure out the artifact type by analyzing the content-type.
     *
     * @param contentType the content type header
     */
    private static ArtifactType getArtifactTypeFromContentType(String contentType) {
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON) && contentType.indexOf(';') != -1) {
            String[] split = contentType.split(";");
            if (split.length > 1) {
                for (String s : split) {
                    if (s.contains("artifactType=")) {
                        String at = s.split("=")[1];
                        try {
                            return ArtifactType.valueOf(at);
                        } catch (IllegalArgumentException e) {
                            throw new BadRequestException("Unsupported artifact type: " + at);
                        }
                    }
                }
            }
        }
        if (contentType != null && contentType.contains("x-proto")) {
            return ArtifactType.PROTOBUF;
        }
        if (contentType != null && contentType.contains("graphql")) {
            return ArtifactType.GRAPHQL;
        }
        return null;
    }

    private CompletionStage<ArtifactMetaData> handleIfExists(String groupId, String artifactId, String version,
            IfExists ifExists, ContentHandle content, String contentType, boolean canonical) {
        final ArtifactMetaData artifactMetaData = getArtifactMetaData(groupId, artifactId);
        if (ifExists == null) {
            ifExists = IfExists.FAIL;
        }

        switch (ifExists) {
            case UPDATE:
                return updateArtifactInternal(groupId, artifactId, version, content, contentType);
            case RETURN:
                return CompletableFuture.completedFuture(artifactMetaData);
            case RETURN_OR_UPDATE:
                return handleIfExistsReturnOrUpdate(groupId, artifactId, version, content, contentType, canonical);
            default:
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }
    }

    private CompletionStage<ArtifactMetaData> handleIfExistsReturnOrUpdate(String groupId, String artifactId, String version,
            ContentHandle content, String contentType, boolean canonical) {
        try {
            ArtifactVersionMetaDataDto mdDto = this.storage.getArtifactVersionMetaData(gidOrNull(groupId), artifactId, canonical, content);
            ArtifactMetaData md = V2ApiUtil.dtoToMetaData(gidOrNull(groupId), artifactId, null, mdDto);
            return CompletableFuture.completedFuture(md);
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - we'll update the artifact if there is no matching content already there.
        }
        return updateArtifactInternal(groupId, artifactId, version, content, contentType);
    }

    private CompletionStage<ArtifactMetaData> updateArtifactInternal(String groupId, String artifactId, String version,
            ContentHandle content, String contentType) {

        if (ContentTypeUtil.isApplicationYaml(contentType)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(gidOrNull(groupId), artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(gidOrNull(groupId), artifactId, version, artifactType, content)
            .thenApply(dto -> V2ApiUtil.dtoToMetaData(gidOrNull(groupId), artifactId, artifactType, dto));
    }

    private String gidOrNull(String groupId) {
        if ("default".equalsIgnoreCase(groupId)) {
            return null;
        }
        return groupId;
    }
}
