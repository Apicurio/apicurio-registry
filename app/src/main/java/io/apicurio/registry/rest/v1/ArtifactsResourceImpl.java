/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.rest.v1;

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v1.beans.EditableMetaData;
import io.apicurio.registry.rest.v1.beans.IfExistsType;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.rest.v1.beans.UpdateState;
import io.apicurio.registry.rest.v1.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.GroupsResourceImpl;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactIdException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import io.apicurio.registry.utils.ArtifactIdValidator;

/**
 * Implements the {@link ArtifactsResource} interface.
 *
 * Note: this class is deprecated in favor of v2 of the REST API.  See {@link GroupsResourceImpl} instead.
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
@ApplicationScoped
@RestMetricsResponseFilteredNameBinding
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT}, reusable = true)
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT}, reusable = true)
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS, reusable = true)
@Logged
@Deprecated
public class ArtifactsResourceImpl implements ArtifactsResource, Headers {
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

    private static final int GET_ARTIFACT_IDS_LIMIT = 10000;

    /**
     * Make sure this is ONLY used when request instance is active.
     * e.g. in actual http request
     */
    private String getContentType() {
        return request.getContentType();
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

    private CompletionStage<ArtifactMetaData> handleIfExists(
            ArtifactType artifactType,
            String artifactId,
            IfExistsType ifExists,
            ContentHandle content,
            String ct, boolean canonical) {
        final ArtifactMetaData artifactMetaData = getArtifactMetaData(artifactId);
        if (ifExists == null) {
            ifExists = IfExistsType.FAIL;
        }

        switch (ifExists) {
            case UPDATE:
                return updateArtifactInternal(artifactId, artifactType, content, ct);
            case RETURN:
                return CompletableFuture.completedFuture(artifactMetaData);
            case RETURN_OR_UPDATE:
                return handleIfExistsReturnOrUpdate(artifactId, artifactType, content, ct, canonical);
            default:
                throw new ArtifactAlreadyExistsException(null, artifactId);
        }
    }

    private CompletionStage<ArtifactMetaData> handleIfExistsReturnOrUpdate(
            String artifactId,
            ArtifactType artifactType,
            ContentHandle content,
            String ct, boolean canonical) {
        try {
            ArtifactVersionMetaDataDto mdDto = this.storage.getArtifactVersionMetaData(null, artifactId, canonical, content);
            ArtifactMetaData md = V1ApiUtil.dtoToMetaData(artifactId, artifactType, mdDto);
            return CompletableFuture.completedFuture(md);
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - we'll update the artifact if there is no matching content already there.
        }
        return updateArtifactInternal(artifactId, artifactType, content, ct);
    }

    public void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String artifactId, String version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, null, artifactId, version, builder);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactState(java.lang.String, io.apicurio.registry.rest.v1.beans.UpdateState)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void updateArtifactState(String artifactId, UpdateState data) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(data.getState());
        storage.updateArtifactState(null, artifactId, data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#listArtifacts()
     */
    @Override
    public List<String> listArtifacts() {
    	return new ArrayList<>(storage.getArtifactIds(GET_ARTIFACT_IDS_LIMIT));
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactVersionState(java.lang.String, java.lang.Integer, io.apicurio.registry.rest.v1.beans.UpdateState)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void updateArtifactVersionState(String artifactId, Integer version, UpdateState data) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(data.getState());
        Objects.requireNonNull(version);

        storage.updateArtifactState(null, artifactId, VersionUtil.toString(version), data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#testUpdateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, java.io.InputStream)
     */
    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, ct);
        rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.UPDATE);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#createArtifact(io.apicurio.registry.types.ArtifactType, java.lang.String, io.apicurio.registry.rest.v1.v1.beans.IfExistsType, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType xRegistryArtifactType,
            String xRegistryArtifactId, IfExistsType ifExists, Boolean canonical, InputStream data) {
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
            rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.CREATE);
            final String finalArtifactId = artifactId;
            return storage.createArtifact(null, artifactId, null, artifactType, content)
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
                            handleIfExists(xRegistryArtifactType, xRegistryArtifactId, ifExists, finalContent, ct, fcanonical) :
                            CompletableFuture.completedFuture(V1ApiUtil.dtoToMetaData(finalArtifactId, artifactType, amd))
                    );
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(xRegistryArtifactType, xRegistryArtifactId, ifExists, content, ct, fcanonical);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getLatestArtifact(java.lang.String)
     */
    @Override
    public Response getLatestArtifact(String artifactId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(null, artifactId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(null, artifactId);
        }
        StoredArtifactDto artifact = storage.getArtifact(null, artifactId);

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
        checkIfDeprecated(metaData::getState, artifactId, metaData.getVersion(), builder);
        return builder.build();
    }

    private CompletionStage<ArtifactMetaData> updateArtifactInternal(String artifactId,
            ArtifactType xRegistryArtifactType, ContentHandle content, String ct) {
        Objects.requireNonNull(artifactId);
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, ct);
        rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(null, artifactId, null, artifactType, content)
            .thenApply(dto -> V1ApiUtil.dtoToMetaData(artifactId, artifactType, dto));
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifact(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        return updateArtifactInternal(artifactId, xRegistryArtifactType, content, getContentType());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#deleteArtifact(java.lang.String)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void deleteArtifact(String artifactId) {
        storage.deleteArtifact(null, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#listArtifactVersions(java.lang.String)
     */
    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        List<String> versions = storage.getArtifactVersions(null, artifactId);
        return versions.stream().map(vstr -> VersionUtil.toLong(vstr)).collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#createArtifactVersion(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, ct);
        rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(null, artifactId, null, artifactType, content)
                .thenApply(amd -> V1ApiUtil.dtoToVersionMetaData(artifactId, artifactType, amd));
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public Response getArtifactVersion(Integer version, String artifactId) {
        String sversion = VersionUtil.toString(version);
        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(null, artifactId, sversion);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(null, artifactId, sversion);
        }
        StoredArtifactDto artifact = storage.getArtifactVersion(null, artifactId, sversion);

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
        checkIfDeprecated(metaData::getState, artifactId, sversion, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#listArtifactRules(java.lang.String)
     */
    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        return storage.getArtifactRules(null, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#createArtifactRule(java.lang.String, io.apicurio.registry.rest.v1.v1.beans.Rule)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void createArtifactRule(String artifactId, Rule data) {
        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());
        storage.createArtifactRule(null, artifactId, data.getType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#deleteArtifactRules(java.lang.String)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void deleteArtifactRules(String artifactId) {
        storage.deleteArtifactRules(null, artifactId);
    }


    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactRuleConfig(io.apicurio.registry.types.RuleType, java.lang.String)
     */
    @Override
    public Rule getArtifactRuleConfig(RuleType rule, String artifactId) {
        RuleConfigurationDto dto = storage.getArtifactRule(null, artifactId, rule);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setType(rule);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactRuleConfig(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v1.beans.Rule)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public Rule updateArtifactRuleConfig(String artifactId, RuleType rule, Rule data) {
        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateArtifactRule(null, artifactId, rule, dto);
        Rule rval = new Rule();
        rval.setType(rule);
        rval.setConfig(data.getConfig());
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void deleteArtifactRule(String artifactId, RuleType rule) {
        storage.deleteArtifactRule(null, artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(null, artifactId);
        return V1ApiUtil.dtoToMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaDataByContent(String artifactId, Boolean canonical,
            InputStream data) {
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

        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(null, artifactId, canonical, content);
        return V1ApiUtil.dtoToVersionMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactMetaData(java.lang.String, io.apicurio.registry.rest.v1.v1.beans.EditableMetaData)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setProperties(data.getProperties());
        storage.updateArtifactMetaData(null, artifactId, dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactVersionMetaData(java.lang.Integer, java.lang.String)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(null, artifactId, VersionUtil.toString(version));
        return V1ApiUtil.dtoToVersionMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactVersionMetaData(java.lang.String, java.lang.Integer, io.apicurio.registry.rest.v1.beans.EditableMetaData)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData data) {
        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setProperties(data.getProperties());
        storage.updateArtifactVersionMetaData(null, artifactId, VersionUtil.toString(version), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#deleteArtifactVersionMetaData(java.lang.String, java.lang.Integer)
     */
    @Override
    @Authorized(AuthorizedStyle.ArtifactOnly)
    public void deleteArtifactVersionMetaData(String artifactId, Integer version) {
        storage.deleteArtifactVersionMetaData(null, artifactId, VersionUtil.toString(version));
    }
}
