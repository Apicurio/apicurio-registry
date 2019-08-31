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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.apicurio.registry.ArtifactIdGenerator;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;

/**
 * Implements the {@link ArtifactsResource} interface.
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
public class ArtifactsResourceImpl implements ArtifactsResource {
    
    @Inject
    @Current
    RegistryStorage storage;
    
    @Inject
    RulesService rulesService;

    @Inject
    ArtifactIdGenerator idGenerator;
    
    @Context
    HttpServletRequest request;
    
    private static final String toString(InputStream stream) {
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }
    
    /**
     * Figures out the artifact type in the following order of precedent:
     * 
     *  1) The provided X-Registry-ArtifactType header
     *  2) A hint provided in the Content-Type header
     *  3) Determined from the content itself
     * 
     * @param content
     * @param xArtifactType
     * @param request
     */
    private static final ArtifactType determineArtifactType(String content, ArtifactType xArtifactType, HttpServletRequest request) {
        ArtifactType artifactType = xArtifactType;
        if (artifactType == null) {
            artifactType = getArtifactTypeFromContentType(request);
            if (artifactType == null) {
                // TODO we need to figure out what type of content is being added by actually analyzing the content itself
                artifactType = ArtifactType.avro;
            }
        }
        return artifactType;
    }

    /**
     * Tries to figure out the artiact type by analyzing the content-type.
     * @param request
     */
    private static ArtifactType getArtifactTypeFromContentType(HttpServletRequest request) {
        String contentType = request.getHeader("Content-Type");
        // TODO handle protobuf here as well - it's the only one that's not JSON
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON) && contentType.indexOf(';') != -1) {
            String [] split = contentType.split(";");
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
        return null;
    }

    /**
     * Creates a jax-rs meta-data entity from the id, type, and storage meta-data.
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    private static ArtifactMetaData dtoToMetaData(String artifactId, ArtifactType artifactType,
            ArtifactMetaDataDto dto) {
        ArtifactMetaData metaData = new ArtifactMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setId(artifactId);
        metaData.setModifiedBy(dto.getModifiedBy());
        metaData.setModifiedOn(dto.getModifiedOn());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        return metaData;
    }
    
    /**
     * Creates a jax-rs version meta-data entity from the id, type, and storage meta-data.
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    private static VersionMetaData dtoToVersionMetaData(String artifactId, ArtifactType artifactType,
            ArtifactMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        return metaData;
    }

    /**
     * Creates a jax-rs version meta-data entity from the id, type, and storage meta-data.
     * @param artifactId
     * @param artifactType
     * @param dto
     */
    private static VersionMetaData dtoToVersionMetaData(String artifactId, ArtifactType artifactType,
            ArtifactVersionMetaDataDto dto) {
        VersionMetaData metaData = new VersionMetaData();
        metaData.setCreatedBy(dto.getCreatedBy());
        metaData.setCreatedOn(dto.getCreatedOn());
        metaData.setDescription(dto.getDescription());
        metaData.setName(dto.getName());
        metaData.setType(artifactType);
        metaData.setVersion(dto.getVersion());
        return metaData;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifact(io.apicurio.registry.types.ArtifactType, java.lang.String, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData createArtifact(ArtifactType xRegistryArtifactType, String xRegistryArtifactId,
            InputStream data) {
        String artifactId = xRegistryArtifactId;
        if (artifactId == null || artifactId.trim().isEmpty()) {
            artifactId = idGenerator.generate();
        }
        String content = toString(data);
        
        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.CREATE);
        ArtifactMetaDataDto dto = storage.createArtifact(artifactId, artifactType, content);

        return dtoToMetaData(artifactId, artifactType, dto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getLatestArtifact(java.lang.String)
     */
    @Override
    public Response getLatestArtifact(String artifactId) {
        StoredArtifact artifact = storage.getArtifact(artifactId);
        // TODO support protobuf - the content-type will be different for protobuff artifacts
        return Response.ok(artifact.content, MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifact(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    public ArtifactMetaData updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        String content = toString(data);
        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
        ArtifactMetaDataDto dto = storage.updateArtifact(artifactId, artifactType, content);
        
        return dtoToMetaData(artifactId, artifactType, dto);
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
        List<Long> rval = new ArrayList<Long>(versions.size());
        rval.addAll(versions);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifactVersion(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    public VersionMetaData createArtifactVersion(String artifactId, ArtifactType xRegistryArtifactType,
            InputStream data) {
        String content = toString(data);
        ArtifactType artifactType = determineArtifactType(content, xRegistryArtifactType, request);
        rulesService.applyRules(artifactId, artifactType, content, RuleApplicationType.UPDATE);
        ArtifactMetaDataDto metaDataDto = storage.updateArtifact(artifactId, artifactType, content);
        return dtoToVersionMetaData(artifactId, artifactType, metaDataDto);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactVersion(java.lang.Integer, java.lang.String)
     */
    @Override
    public Response getArtifactVersion(Integer version, String artifactId) {
        StoredArtifact artifact = storage.getArtifactVersion(artifactId, version);
        // TODO support protobuff - the content-type will be different for protobuff artifacts
        Response response = Response.ok(artifact.content, MediaType.APPLICATION_JSON_TYPE).build();
        return response;
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
    public List<String> listArtifactRules(String artifactId) {
        return storage.getArtifactRules(artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#createArtifactRule(java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public void createArtifactRule(String artifactId, Rule data) {
        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());
        storage.createArtifactRule(artifactId, data.getName(), config);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) {
        storage.deleteArtifactRules(artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactRuleConfig(java.lang.String, java.lang.String)
     */
    @Override
    public Rule getArtifactRuleConfig(String rule, String artifactId) {
        RuleConfigurationDto dto = storage.getArtifactRule(artifactId, rule);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setName(rule);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#updateArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.rest.beans.Rule)
     */
    @Override
    public Rule updateArtifactRuleConfig(String rule, String artifactId, Rule data) {
        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateArtifactRule(artifactId, rule, dto);
        Rule rval = new Rule();
        rval.setName(rule);
        rval.setConfig(data.getConfig());
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#deleteArtifactRule(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRule(String rule, String artifactId) {
        storage.deleteArtifactRule(artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.ArtifactsResource#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(artifactId);
        return dtoToMetaData(artifactId, dto.getType(), dto);
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
        return dtoToVersionMetaData(artifactId, dto.getType(), dto);
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
