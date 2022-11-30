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

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
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
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import io.apicurio.registry.utils.ArtifactIdValidator;

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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_CANONICAL;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_EDITABLE_METADATA;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_IF_EXISTS;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_UPDATE_STATE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;

/**
 * Implements the {@link ArtifactsResource} interface.
 *
 * Note: this class is deprecated in favor of v2 of the REST API.  See {@link GroupsResourceImpl} instead.
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
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

    @Inject
    ArtifactTypeUtilProviderFactory factory;

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

    private ArtifactMetaData handleIfExists(
            String artifactType,
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
                return artifactMetaData;
            case RETURN_OR_UPDATE:
                return handleIfExistsReturnOrUpdate(artifactId, artifactType, content, ct, canonical);
            default:
                throw new ArtifactAlreadyExistsException(null, artifactId);
        }
    }

    private ArtifactMetaData handleIfExistsReturnOrUpdate(
            String artifactId,
            String artifactType,
            ContentHandle content,
            String ct, boolean canonical) {
        try {
            ArtifactVersionMetaDataDto mdDto = this.storage.getArtifactVersionMetaData(null, artifactId, canonical, content);
            ArtifactMetaData md = V1ApiUtil.dtoToMetaData(artifactId, artifactType, mdDto);
            return md;
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
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_UPDATE_STATE})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void updateArtifactState(String artifactId, UpdateState data) {
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(data.getState());
        storage.updateArtifactState(null, artifactId, data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#listArtifacts()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public List<String> listArtifacts() {
    	return new ArrayList<>(storage.getArtifactIds(GET_ARTIFACT_IDS_LIMIT));
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactVersionState(java.lang.String, java.lang.Integer, io.apicurio.registry.rest.v1.beans.UpdateState)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION, "2", KEY_UPDATE_STATE})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
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
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public void testUpdateArtifact(String artifactId, String xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = ArtifactTypeUtil.determineArtifactType(content, xRegistryArtifactType, ct, factory.getAllArtifactTypes());
        rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.UPDATE, Collections.emptyMap());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#createArtifact (io.apicurio.registry.types.ArtifactType, java.lang.String, io.apicurio.registry.rest.v1.v1.beans.IfExistsType, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_TYPE, "1", KEY_ARTIFACT_ID, "2", KEY_IF_EXISTS, "3", KEY_CANONICAL})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Write)
    public ArtifactMetaData createArtifact(String xRegistryArtifactType,
            String xRegistryArtifactId, IfExistsType ifExists, Boolean canonical, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
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
            if (ContentTypeUtil.isApplicationYaml(ct)) {
                content = ContentTypeUtil.yamlToJson(content);
            }

            String artifactType = ArtifactTypeUtil.determineArtifactType(content, xRegistryArtifactType, ct, factory.getAllArtifactTypes());
            rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.CREATE, Collections.emptyMap());
            final String finalArtifactId = artifactId;
            ArtifactMetaDataDto amd = storage.createArtifact(null, artifactId, null, artifactType, content, null);
            return V1ApiUtil.dtoToMetaData(finalArtifactId, artifactType, amd);
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(xRegistryArtifactType, xRegistryArtifactId, ifExists, content, ct, fcanonical);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getLatestArtifact(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public Response getLatestArtifact(String artifactId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(null, artifactId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(null, artifactId);
        }
        StoredArtifactDto artifact = storage.getArtifact(null, artifactId);

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        Response.ResponseBuilder builder = Response.ok(artifact.getContent(), contentType);
        checkIfDeprecated(metaData::getState, artifactId, metaData.getVersion(), builder);
        return builder.build();
    }

    private ArtifactMetaData updateArtifactInternal(String artifactId,
            String xRegistryArtifactType, ContentHandle content, String ct) {
        Objects.requireNonNull(artifactId);
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = ArtifactTypeUtil.determineArtifactType(content, xRegistryArtifactType, ct, factory.getAllArtifactTypes());
        rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.UPDATE, Collections.emptyMap());
        ArtifactMetaDataDto dto = storage.updateArtifact(null, artifactId, null, artifactType, content, null);
        return V1ApiUtil.dtoToMetaData(artifactId, artifactType, dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifact(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_ARTIFACT_TYPE})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public ArtifactMetaData updateArtifact(String artifactId, String xRegistryArtifactType, InputStream data) {
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
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void deleteArtifact(String artifactId) {
        storage.deleteArtifact(null, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#listArtifactVersions(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public List<Long> listArtifactVersions(String artifactId) {
        List<String> versions = storage.getArtifactVersions(null, artifactId);
        return versions.stream().map(vstr -> VersionUtil.toLong(vstr)).collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#createArtifactVersion(java.lang.String, ArtifactType, java.io.InputStream)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_ARTIFACT_TYPE})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public VersionMetaData createArtifactVersion(String artifactId, String xRegistryArtifactType, InputStream data) {
        Objects.requireNonNull(artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = ArtifactTypeUtil.determineArtifactType(content, xRegistryArtifactType, ct, factory.getAllArtifactTypes());
        rulesService.applyRules(null, artifactId, artifactType, content, RuleApplicationType.UPDATE, Collections.emptyMap());
        ArtifactMetaDataDto amd = storage.updateArtifact(null, artifactId, null, artifactType, content, null);
        return V1ApiUtil.dtoToVersionMetaData(artifactId, artifactType, amd);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactVersion(java.lang.String, java.lang.Integer)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public Response getArtifactVersion(String artifactId, Integer version) {
        String sversion = VersionUtil.toString(version);
        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(null, artifactId, sversion);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(null, artifactId, sversion);
        }
        StoredArtifactDto artifact = storage.getArtifactVersion(null, artifactId, sversion);

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        Response.ResponseBuilder builder = Response.ok(artifact.getContent(), contentType);
        checkIfDeprecated(metaData::getState, artifactId, sversion, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#listArtifactRules(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public List<RuleType> listArtifactRules(String artifactId) {
        return storage.getArtifactRules(null, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#createArtifactRule (java.lang.String, io.apicurio.registry.rest.v1.v1.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_RULE})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void createArtifactRule(String artifactId, Rule data) {
        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());
        if (!storage.isArtifactExists(null, artifactId)) {
            throw new ArtifactNotFoundException(null, artifactId);
        }
        storage.createArtifactRule(null, artifactId, data.getType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#deleteArtifactRules(java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void deleteArtifactRules(String artifactId) {
        storage.deleteArtifactRules(null, artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactRuleConfig(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public Rule getArtifactRuleConfig(String artifactId, RuleType rule) {
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
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_RULE_TYPE, "2", KEY_RULE})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
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
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_RULE_TYPE})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void deleteArtifactRule(String artifactId, RuleType rule) {
        storage.deleteArtifactRule(null, artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactMetaData(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(null, artifactId);
        return V1ApiUtil.dtoToMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaDataByContent(String artifactId, Boolean canonical, InputStream data) {
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
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactMetaData (java.lang.String, io.apicurio.registry.rest.v1.v1.beans.EditableMetaData)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_EDITABLE_METADATA})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setProperties(data.getProperties());
        storage.updateArtifactMetaData(null, artifactId, dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#getArtifactVersionMetaData(java.lang.String, java.lang.Integer)
     */
    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version) {
        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(null, artifactId, VersionUtil.toString(version));
        return V1ApiUtil.dtoToVersionMetaData(artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v1.ArtifactsResource#updateArtifactVersionMetaData(java.lang.String, java.lang.Integer, io.apicurio.registry.rest.v1.beans.EditableMetaData)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION,  "2", KEY_EDITABLE_METADATA})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
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
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public void deleteArtifactVersionMetaData(String artifactId, Integer version) {
        storage.deleteArtifactVersionMetaData(null, artifactId, VersionUtil.toString(version));
    }
}
