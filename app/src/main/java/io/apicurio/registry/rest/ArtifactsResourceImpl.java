/*
 * Copyright 2019 Red Hat
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
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.utils.ProtoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Implements the {@link ArtifactsResource} interface.
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
public class ArtifactsResourceImpl implements ArtifactsResource {
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
        return null;
    }

    private CompletionStage<ArtifactMetaDataDto> indexArtifact(String artifactId, ContentHandle content, ArtifactMetaDataDto amdd) throws CompletionException {
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
            return searchClient.index(artifact).thenApply(sr -> {
                if (sr.ok()) {
                    log.info("Artifact {}/{} successfully indexed", artifactId, amdd.getVersion());
                } else {
                    log.warn("Artifact {}/{} not indexed, status: {}", artifactId, amdd.getVersion(), sr.status());
                }
                return amdd;
            });
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(state);
        storage.updateArtifactState(artifactId, state);
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(state);
        Objects.requireNonNull(version);
        storage.updateArtifactState(artifactId, state, version);
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifact(io.apicurio.registry.types.ArtifactType, java.lang.String, java.io.InputStream)
     */
    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType xRegistryArtifactType, String xRegistryArtifactId,
                                                            InputStream data) {
        String artifactId = xRegistryArtifactId;
        if (artifactId == null || artifactId.trim().isEmpty()) {
            artifactId = idGenerator.generate();
        }
        ContentHandle content = ContentHandle.create(data);

        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.CREATE);
        String finalArtifactId = artifactId;
        return storage.createArtifact(artifactId, artifactType, content)
                      .thenCompose(amdd -> indexArtifact(finalArtifactId, content, amdd))
                      .thenApply(dto -> DtoUtil.dtoToMetaData(finalArtifactId, artifactType, dto));
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getLatestArtifact(java.lang.String)
     */
    @Override
    public Response getLatestArtifact(String artifactId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(artifactId);
        StoredArtifact artifact = storage.getArtifact(artifactId);
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (metaData.getType() == ArtifactType.PROTOBUF) {
            contentType = ArtifactMediaTypes.PROTO;
        }
        return Response.ok(artifact.content, contentType).build();
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifact(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(artifactId, artifactType, content)
                      .thenCompose(amdd -> indexArtifact(artifactId, content, amdd))
                      .thenApply(dto -> DtoUtil.dtoToMetaData(artifactId, artifactType, dto));
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
        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
        return storage.updateArtifact(artifactId, artifactType, content)
                      .thenCompose(amdd -> indexArtifact(artifactId, content, amdd))
                      .thenApply(dto -> DtoUtil.dtoToVersionMetaData(artifactId, artifactType, dto));
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public Response getArtifactVersion(Integer version, String artifactId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(artifactId);
        StoredArtifact artifact = storage.getArtifactVersion(artifactId, version);

        // protobuf - the content-type will be different for protobuf artifacts
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (metaData.getType() == ArtifactType.PROTOBUF) {
            contentType = ArtifactMediaTypes.PROTO;
        }
        return Response.ok(artifact.content, contentType).build();
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(Integer version, String artifactId) {
        storage.deleteArtifactVersion(artifactId, version);
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
