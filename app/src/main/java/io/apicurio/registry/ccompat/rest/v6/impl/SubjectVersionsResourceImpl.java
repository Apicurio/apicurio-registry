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

package io.apicurio.registry.ccompat.rest.v6.impl;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;
import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.SKIP_DISABLED_LATEST;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.apicurio.registry.ccompat.rest.error.ReferenceExistsException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SchemaSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.impl.AbstractResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactTypeException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.VersionUtil;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaId;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.v6.SubjectVersionsResource;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

/**
 * @author Ales Justin
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {


    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public List<Integer> listVersions(String subject) throws Exception {
            return getStorage().getArtifactVersions(null, subject).stream().map(VersionUtil::toLong).map(getConverter()::convertUnsigned).sorted().collect(Collectors.toList());
        }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public SchemaId register(String subject, SchemaInfo request) throws Exception {
        // Check to see if this content is already registered - return the global ID of that content
        // if it exists.  If not, then register the new content.
        long sid = -1;
        boolean idFound = false;
        if (null == request) {
            throw new UnprocessableEntityException("The schema provided is null.");
        }

        final Map<String, ContentHandle> resolvedReferences = resolveReferences(request.getReferences());

        try {
            ArtifactVersionMetaDataDto dto = lookupSchema(null, subject, request.getSchema(), request.getReferences(), request.getSchemaType(), false);
            if (dto.getState().equals(ArtifactState.DISABLED)) {
                throw new ArtifactNotFoundException(null, subject);
            }
            sid = getCconfig().getLegacyIdModeEnabled().get() ? dto.getGlobalId() : dto.getContentId();
            idFound = true;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }

        if (!idFound) {
            try {
                // We validate the schema at creation time by inferring the type from the content
                final String artifactType = ArtifactTypeUtil.determineArtifactType(ContentHandle.create(request.getSchema()), null, null, resolvedReferences, getFactory().getAllArtifactTypes());
                if (request.getSchemaType() != null && !artifactType.equals(request.getSchemaType())) {
                    throw new UnprocessableEntityException(String.format("Given schema is not from type: %s", request.getSchemaType()));
                }

                ArtifactMetaDataDto artifactMeta = createOrUpdateArtifact(subject, request.getSchema(), artifactType, request.getReferences(), null);
                sid = getCconfig().getLegacyIdModeEnabled().get() ? artifactMeta.getGlobalId() : artifactMeta.getContentId();
            } catch (InvalidArtifactTypeException ex) {
                //If no artifact type can be inferred, throw invalid schema ex
                throw new UnprocessableEntityException(ex.getMessage());
            }
        }

        int id = getConverter().convertUnsigned(sid);
        return new SchemaId(id);
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public Schema getSchemaByVersion(
            String subject,
            String version) throws Exception {
        return getSchema(null, subject, version, false);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public int deleteSchemaVersion(
            String subject,
            String versionString) throws Exception {

        try {
            if (doesArtifactExist(subject, null)) {

                return VersionUtil.toInteger(parseVersionString(subject, versionString, null, version -> {
                    List<Long> globalIdsReferencingSchema = getStorage().getGlobalIdsReferencingArtifact(null, subject, version);
                    ArtifactVersionMetaDataDto avmd = getStorage().getArtifactVersionMetaData(null, subject, version);
                    if (globalIdsReferencingSchema.isEmpty() || areAllSchemasDisabled(globalIdsReferencingSchema)) {
                        return processDeleteVersion(subject, versionString, null, version, false, avmd);
                    } else {
                        //There are other schemas referencing this one, it cannot be deleted.
                        throw new ReferenceExistsException(String.format("There are subjects referencing %s", subject));
                    }

                }));
            } else {
                throw new ArtifactNotFoundException(null, subject);
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex);
        }
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public String getSchemaOnly(
            String subject,
            String version) throws Exception {
        return getSchema(null, subject, version, false).getSchema();
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public List<Long> getSchemasReferencedBy(String subject, String versionString) throws Exception {
        if (getCconfig().getLegacyIdModeEnabled().get()) {
            return parseVersionString(subject, versionString, null, version -> getStorage().getGlobalIdsReferencingArtifact(null, subject, version));
        }

        return parseVersionString(subject, versionString, null
                , version -> getStorage().getContentIdsReferencingArtifact(null, subject, version));
    }

    private String processDeleteVersion(String subject, String versionString, String groupId, String version, boolean fpermanent, ArtifactVersionMetaDataDto avmd) {
        if (fpermanent) {
            if (avmd.getState().equals(ArtifactState.ENABLED) || avmd.getState().equals(ArtifactState.DEPRECATED)) {
                throw new SchemaNotSoftDeletedException(String.format("Subject %s version %s must be soft deleted first", subject, versionString));
            } else if (avmd.getState().equals(ArtifactState.DISABLED)) {
                getStorage().deleteArtifactVersion(groupId, subject, version);
            }
        } else {
            if (avmd.getState().equals(ArtifactState.DISABLED)) {
                throw new SchemaSoftDeletedException("Schema is already soft deleted");
            } else {
                getStorage().updateArtifactState(groupId, subject, version, ArtifactState.DISABLED);
            }
        }
        return version;
    }

    protected Schema getSchema(String groupId, String subject, String versionString, boolean deleted) {
        if (doesArtifactExist(subject, groupId) && isArtifactActive(subject, groupId, SKIP_DISABLED_LATEST)) {
            return parseVersionString(subject, versionString, groupId, version -> {
                ArtifactVersionMetaDataDto amd = getStorage().getArtifactVersionMetaData(groupId, subject, version);
                if (amd.getState() != ArtifactState.DISABLED || deleted) {
                    StoredArtifactDto storedArtifact = getStorage().getArtifactVersion(groupId, subject, amd.getVersion());
                    return getConverter().convert(subject, storedArtifact);
                } else {
                    throw new VersionNotFoundException(groupId, subject, version);
                }
            });
        } else {
            throw new ArtifactNotFoundException(groupId, subject);
        }
    }
}
