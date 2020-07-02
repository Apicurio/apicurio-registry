/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rest;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.storage.*;
import io.apicurio.registry.types.*;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.utils.ProtoUtil;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * Implements the {@link ArtifactsResource} interface.
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT}, reusable = true)
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT}, reusable = true)
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS, reusable = true)
@Logged
@Authenticated
public class ArtifactsResourceImpl implements ArtifactsResource, Headers {
    private static final Logger log = LoggerFactory.getLogger(ArtifactsResourceImpl.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    ArtifactIdGenerator idGenerator;

    @Context
    HttpServletRequest request;

    @Inject
    @Current
    SearchClient searchClient;

    private static final int GET_ARTIFACT_IDS_LIMIT = 10000;

    /**
     * Figures out the artifact type in the following order of precedent:
     * <p>
     * 1) The provided X-Registry-ArtifactType header
     * 2) A hint provided in the Content-Type header
     * 3) Determined from the content itself
     *
     * @param content the content
     * @param xArtifactType the artifact type
     * @param request http request
     */
    private static ArtifactType determineArtifactType(ContentHandle content, ArtifactType xArtifactType, HttpServletRequest request) {
        ArtifactType artifactType = xArtifactType;
        if (artifactType == null) {
            artifactType = getArtifactTypeFromContentType(request);
            if (artifactType == null) {
                String contentType = request.getContentType();
                artifactType = ArtifactTypeUtil.discoverType(content, contentType);
            }
        }
        return artifactType;
    }

    /**
     * Tries to figure out the artifact type by analyzing the content-type.
     *
     * @param request http request
     */
    private static ArtifactType getArtifactTypeFromContentType(HttpServletRequest request) {
        String contentType = request.getHeader("Content-Type");
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

    private CompletionStage<ArtifactMetaData> indexArtifact(String artifactId, ContentHandle content, ArtifactMetaData amdd) throws CompletionException {
        try {
            Search.Artifact artifact = Search.Artifact.newBuilder()
                    .setArtifactId(artifactId)
                    .setContent(content.content())
                    .setVersion(amdd.getVersion())
                    .setGlobalId(amdd.getGlobalId())
                    .setName(ProtoUtil.nullAsEmpty(amdd.getName()))
                    .setDescription(ProtoUtil.nullAsEmpty(amdd.getDescription()))
                    .setCreatedBy(ProtoUtil.nullAsEmpty(amdd.getCreatedBy()))
                    .build();
            return searchClient.index(artifact).whenComplete((sr, t) -> {
                if (t != null) {
                    log.error("Artifact {}/{} not indexed, error: {}", artifactId, amdd.getVersion(), t.getMessage());
                } else {
                    if (sr.ok()) {
                        log.info("Artifact {}/{} successfully indexed", artifactId, amdd.getVersion());
                    } else {
                        log.warn("Artifact {}/{} not indexed, status: {}", artifactId, amdd.getVersion(), sr.status());
                    }
                }
            }).thenApply(sr -> amdd);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private CompletionStage<ArtifactMetaData> handleIfExists(ArtifactType artifactType,
                                                             String artifactId, IfExistsType ifExists, ContentHandle content) {
        final ArtifactMetaData artifactMetaData = getArtifactMetaData(artifactId);

        switch (ifExists) {
            case UPDATE:
                return updateArtifactInternal(artifactId, artifactType, content);
            case RETURN:
                return CompletableFuture.completedFuture(artifactMetaData);
            case RETURN_OR_UPDATE:
                return handleIfExistsReturnOrUpdate(artifactId, artifactType, content);
            default:
                throw new ArtifactAlreadyExistsException(artifactId);
        }
    }

    private CompletionStage<ArtifactMetaData> handleIfExistsReturnOrUpdate(String artifactId,
            ArtifactType artifactType, ContentHandle content) {
        try {
            ArtifactMetaDataDto mdDto = this.storage.getArtifactMetaData(artifactId, content);
            ArtifactMetaData md = DtoUtil.dtoToMetaData(artifactId, artifactType, mdDto);
            return CompletableFuture.completedFuture(md);
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - we'll update the artifact if there is no matching content already there.
        }
        return updateArtifactInternal(artifactId, artifactType, content);
    }

    @Override
    public void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String artifactId, Number version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, artifactId, version, builder);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactState(java.lang.String, io.apicurio.registry.rest.beans.UpdateState)
     */
    @Override
    public void updateArtifactState(String artifactId, UpdateState data) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(data.getState());
        storage.updateArtifactState(artifactId, data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#listArtifacts()
     */
    @Override
    public List<String> listArtifacts() {
    	return new ArrayList<>(storage.getArtifactIds(GET_ARTIFACT_IDS_LIMIT));
    }    

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactVersionState(java.lang.Integer, java.lang.String, io.apicurio.registry.rest.beans.UpdateState)
     */
    @Override
    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(data.getState());
        Objects.requireNonNull(version);
        storage.updateArtifactState(artifactId, data.getState(), version);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#testUpdateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, java.io.InputStream)
     */
    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (ContentTypeUtil.isApplicationYaml(request)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifact(io.apicurio.registry.types.ArtifactType, java.lang.String, io.apicurio.registry.rest.beans.IfExistsType, java.io.InputStream)
     */
    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType xRegistryArtifactType,
            String xRegistryArtifactId, IfExistsType ifExists, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        final ContentHandle finalContent = content;
        try {
            String artifactId = xRegistryArtifactId;

            if (artifactId == null || artifactId.trim().isEmpty()) {
                artifactId = idGenerator.generate();
            }
            if (ContentTypeUtil.isApplicationYaml(request)) {
                content = ContentTypeUtil.yamlToJson(content);
            }

            ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
            rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.CREATE);
            final String finalArtifactId = artifactId;
            return storage.createArtifact(artifactId, artifactType, content)
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
                            handleIfExists(xRegistryArtifactType, xRegistryArtifactId, ifExists, finalContent) :
                            CompletableFuture.completedFuture(DtoUtil.dtoToMetaData(finalArtifactId, artifactType, amd))
                    )
                    .thenCompose(amdd -> indexArtifact(finalArtifactId, finalContent, amdd));
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(xRegistryArtifactType, xRegistryArtifactId, ifExists, content)
                    .thenCompose(amdd -> indexArtifact(xRegistryArtifactId, finalContent, amdd));
        }
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getLatestArtifact(java.lang.String)
     */
    @Override
    public Response getLatestArtifact(String artifactId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(artifactId);
        StoredArtifact artifact = storage.getArtifact(artifactId);

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

    private CompletionStage<ArtifactMetaData> updateArtifactInternal(String artifactId, ArtifactType xRegistryArtifactType, ContentHandle content) {
        Objects.requireNonNull(artifactId);
        if (ContentTypeUtil.isApplicationYaml(request)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(artifactId, artifactType, content).thenApply(dto -> DtoUtil.dtoToMetaData(artifactId, artifactType, dto));
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifact(java.lang.String, ArtifactType, java.io.InputStream)
     */
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        return updateArtifactInternal(artifactId, xRegistryArtifactType, content)
                .thenCompose(amdd -> indexArtifact(artifactId, content, amdd));
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifact(java.lang.String)
     */
    @Override
    public void deleteArtifact(String artifactId) {
        storage.deleteArtifact(artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#listArtifactVersions(java.lang.String)
     */
    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        SortedSet<Long> versions = storage.getArtifactVersions(artifactId);
        return new ArrayList<>(versions);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifactVersion(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (ContentTypeUtil.isApplicationYaml(request)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
        final ContentHandle finalContent = content;
        return storage.updateArtifact(artifactId, artifactType, content)
                .thenCompose(amdd -> indexArtifact(artifactId, finalContent, DtoUtil.dtoToMetaData(artifactId, artifactType, amdd)))
                .thenApply(amd -> DtoUtil.dtoToVersionMetaData(artifactId, artifactType, amd));
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public Response getArtifactVersion(Integer version, String artifactId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(artifactId);
        StoredArtifact artifact = storage.getArtifactVersion(artifactId, version);

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
        checkIfDeprecated(metaData::getState, artifactId, version, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#listArtifactRules(java.lang.String)
     */
    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        return storage.getArtifactRules(artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifactRule(java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public void createArtifactRule(String artifactId, Rule data) {
        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());
        storage.createArtifactRule(artifactId, data.getType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) {
        storage.deleteArtifactRules(artifactId);
    }


    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactRuleConfig(io.apicurio.registry.types.RuleType, java.lang.String)
     */
    @Override
    public Rule getArtifactRuleConfig(RuleType rule, String artifactId) {
        RuleConfigurationDto dto = storage.getArtifactRule(artifactId, rule);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setType(rule);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactRuleConfig(io.apicurio.registry.types.RuleType, java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateArtifactRuleConfig(RuleType rule, String artifactId, Rule data) {
        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateArtifactRule(artifactId, rule, dto);
        Rule rval = new Rule();
        rval.setType(rule);
        rval.setConfig(data.getConfig());
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactRule(io.apicurio.registry.types.RuleType, java.lang.String)
     */
    @Override
    public void deleteArtifactRule(RuleType rule, String artifactId) {
        storage.deleteArtifactRule(artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(artifactId);
        return DtoUtil.dtoToMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactMetaDataByContent(java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        if (ContentTypeUtil.isApplicationYaml(request)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(artifactId, content);
        return DtoUtil.dtoToMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactMetaData(java.lang.String, io.apicurio.registry.rest.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        storage.updateArtifactMetaData(artifactId, dto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactVersionMetaData(java.lang.Integer, java.lang.String)
     */
    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(artifactId, version);
        return DtoUtil.dtoToVersionMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactVersionMetaData(java.lang.Integer, java.lang.String, io.apicurio.registry.rest.beans.EditableMetaData)
     */
    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        storage.updateArtifactVersionMetaData(artifactId, version.longValue(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactVersionMetaData(java.lang.Integer, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        storage.deleteArtifactVersionMetaData(artifactId, version);
    }
}
