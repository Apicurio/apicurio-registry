/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.common.apps.util.Pair;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaId;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.error.ReferenceExistsException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SchemaSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.SubjectVersionsResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactTypeException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;
import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;
import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.SKIP_DISABLED_LATEST;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {

    @Inject
    ApiConverter converter;

    // GET /apis/ccompat/v7/subjects/{subject}/versions
    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Integer> listVersions(String subject, String groupId, Boolean deleted) throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);

        if (fdeleted) {
            return storage.getArtifactVersions(ga.getGroupId(), ga.getArtifactId(), DEFAULT).stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted().collect(Collectors.toList());
        } else {
            return storage.getArtifactVersions(ga.getGroupId(), ga.getArtifactId(), SKIP_DISABLED_LATEST).stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted().collect(Collectors.toList());
        }
    }

    // POST /apis/ccompat/v7/subjects/{subject}/versions
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public SchemaId register(String subject, SchemaInfo request, Boolean normalize, String groupId) throws Exception {
        final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
        final GA ga = getGA(groupId, subject);

        // Check to see if this content is already registered - return the global ID of that content
        // if it exists.  If not, then register the new content.
        long sid = -1;
        boolean idFound = false;
        if (null == request) {
            throw new UnprocessableEntityException("The schema provided is null.");
        }

        final Map<String, ContentHandle> resolvedReferences = resolveReferences(request.getReferences());

        try {
            ArtifactVersionMetaDataDto dto = lookupSchema(ga.getGroupId(), ga.getArtifactId(), request.getSchema(), request.getReferences(), request.getSchemaType(), fnormalize);
            if (dto.getState().equals(ArtifactState.DISABLED)) {
                throw new ArtifactNotFoundException(ga.getGroupId(), ga.getArtifactId());
            }
            sid = cconfig.legacyIdModeEnabled.get() ? dto.getGlobalId() : dto.getContentId();
            idFound = true;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }

        if (!idFound) {
            try {
                // We validate the schema at creation time by inferring the type from the content
                final String artifactType = ArtifactTypeUtil.determineArtifactType(ContentHandle.create(request.getSchema()), null, null, resolvedReferences, factory.getAllArtifactTypes());
                if (request.getSchemaType() != null && !artifactType.equals(request.getSchemaType())) {
                    throw new UnprocessableEntityException(String.format("Given schema is not from type: %s", request.getSchemaType()));
                }

                ArtifactMetaDataDto artifactMeta = createOrUpdateArtifact(ga.getArtifactId(), request.getSchema(), artifactType, request.getReferences(), ga.getGroupId());
                sid = cconfig.legacyIdModeEnabled.get() ? artifactMeta.getGlobalId() : artifactMeta.getContentId();
            } catch (InvalidArtifactTypeException ex) {
                //If no artifact type can be inferred, throw invalid schema ex
                throw new UnprocessableEntityException(ex.getMessage());
            }
        }

        int id = converter.convertUnsigned(sid);
        return new SchemaId(id);
    }

    // GET /apis/ccompat/v7/subjects/{subject}/versions/{version}
    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema getSchemaByVersion(String subject, String version, String groupId, Boolean deleted) throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);
        return getSchema(ga.getGroupId(), ga.getArtifactId(), version, fdeleted);
    }

    // DELETE /apis/ccompat/v7/subjects/{subject}/versions/{version}
    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public int deleteSchemaVersion(String subject, String versionString, Boolean permanent, String groupId) throws Exception {
        final GA ga = getGA(groupId, subject);
        try {
            if (doesArtifactExist(ga.getArtifactId(), ga.getGroupId())) {
                final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;

                return VersionUtil.toInteger(parseVersionString(ga.getArtifactId(), versionString, ga.getGroupId(), version -> {
                    List<Long> globalIdsReferencingSchema = storage.getGlobalIdsReferencingArtifact(ga.getGroupId(), ga.getArtifactId(), version);
                    ArtifactVersionMetaDataDto avmd = storage.getArtifactVersionMetaData(ga.getGroupId(), ga.getArtifactId(), version);
                    if (globalIdsReferencingSchema.isEmpty() || areAllSchemasDisabled(globalIdsReferencingSchema)) {
                        return processDeleteVersion(ga.getArtifactId(), versionString, ga.getGroupId(), version, fpermanent, avmd);
                    } else {
                        //There are other schemas referencing this one, it cannot be deleted.
                        throw new ReferenceExistsException(String.format("There are subjects referencing %s", subject));
                    }
                }));
            } else {
                throw new ArtifactNotFoundException(ga.getGroupId(), ga.getArtifactId());
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex);
        }
    }

    private String processDeleteVersion(String artifactId, String versionString, String groupId, String version, boolean fpermanent, ArtifactVersionMetaDataDto avmd) {
        if (fpermanent) {
            if (avmd.getState().equals(ArtifactState.ENABLED) || avmd.getState().equals(ArtifactState.DEPRECATED)) {
                throw new SchemaNotSoftDeletedException(String.format("Subject %s version %s must be soft deleted first", artifactId, versionString));
            } else if (avmd.getState().equals(ArtifactState.DISABLED)) {
                storage.deleteArtifactVersion(groupId, artifactId, version);
            }
        } else {
            if (avmd.getState().equals(ArtifactState.DISABLED)) {
                throw new SchemaSoftDeletedException("Schema is already soft deleted");
            } else {
                storage.updateArtifactState(groupId, artifactId, version, ArtifactState.DISABLED);
            }
        }
        return version;
    }

    // GET /apis/ccompat/v7/subjects/{subject}/versions/{version}/schema
    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public String getSchemaOnly(String subject, String version, String groupId, Boolean deleted) throws Exception {
        final GA ga = getGA(groupId, subject);
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        return getSchema(ga.getGroupId(), ga.getArtifactId(), version, fdeleted).getSchema();
    }

    // GET /apis/ccompat/v7/subjects/{subject}/versions/{version}/referencedBy
    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Long> getSchemasReferencedBy(String subject, String versionString, String groupId) throws Exception {
        final GA ga = getGA(groupId, subject);

        if (cconfig.legacyIdModeEnabled.get()) {
            return parseVersionString(ga.getArtifactId(), versionString, ga.getGroupId(), version -> storage.getGlobalIdsReferencingArtifact(ga.getGroupId(), ga.getArtifactId(), version));
        }

        return parseVersionString(ga.getArtifactId(), versionString, ga.getGroupId(), version -> storage.getContentIdsReferencingArtifact(ga.getGroupId(), ga.getArtifactId(), version));
    }

    protected Schema getSchema(String groupId, String artifactId, String versionString, boolean deleted) {
        if (doesArtifactExist(artifactId, groupId) && isArtifactActive(artifactId, groupId, SKIP_DISABLED_LATEST)) {
            return parseVersionString(artifactId, versionString, groupId, version -> {
                ArtifactVersionMetaDataDto amd = storage.getArtifactVersionMetaData(groupId, artifactId, version);
                if (amd.getState() != ArtifactState.DISABLED || deleted) {
                    StoredArtifactDto storedArtifact = storage.getArtifactVersion(groupId, artifactId, amd.getVersion());
                    return converter.convert(artifactId, storedArtifact, amd.getType());
                } else {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            });
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }
}
