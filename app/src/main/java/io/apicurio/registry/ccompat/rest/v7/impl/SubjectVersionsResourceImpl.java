package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
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
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
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

@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {

    @Inject
    ApiConverter converter;

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Integer> listVersions(String subject, String groupId, Boolean deleted) throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        if (fdeleted) {
            return storage.getArtifactVersions(groupId, subject, DEFAULT).stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted().collect(Collectors.toList());
        } else {
            return storage.getArtifactVersions(groupId, subject, SKIP_DISABLED_LATEST).stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted().collect(Collectors.toList());
        }
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public SchemaId register(String subject, SchemaInfo request, Boolean normalize, String groupId) throws Exception {
        final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;

        // Check to see if this content is already registered - return the global ID of that content
        // if it exists.  If not, then register the new content.
        long sid = -1;
        boolean idFound = false;
        if (null == request) {
            throw new UnprocessableEntityException("The schema provided is null.");
        }

        final Map<String, ContentHandle> resolvedReferences = resolveReferences(request.getReferences());

        try {
            ArtifactVersionMetaDataDto dto = lookupSchema(groupId, subject, request.getSchema(), request.getReferences(), request.getSchemaType(), fnormalize);
            if (dto.getState().equals(VersionState.DISABLED)) {
                throw new ArtifactNotFoundException(groupId, subject);
            }
            sid = cconfig.legacyIdModeEnabled.get() ? dto.getGlobalId() : dto.getContentId();
            idFound = true;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - when it happens just move on and create
        }

        if (!idFound) {
            try {
                ContentHandle schemaContent = ContentHandle.create(request.getSchema());
                String contentType = ContentTypeUtil.determineContentType(schemaContent);

                // We validate the schema at creation time by inferring the type from the content
                final String artifactType = ArtifactTypeUtil.determineArtifactType(ContentHandle.create(request.getSchema()),
                        null, contentType, resolvedReferences, factory.getAllArtifactTypes());
                if (request.getSchemaType() != null && !artifactType.equals(request.getSchemaType())) {
                    throw new UnprocessableEntityException(String.format("Given schema is not from type: %s", request.getSchemaType()));
                }

                ArtifactVersionMetaDataDto artifactMeta = createOrUpdateArtifact(subject, request.getSchema(), artifactType, request.getReferences(), groupId);
                sid = cconfig.legacyIdModeEnabled.get() ? artifactMeta.getGlobalId() : artifactMeta.getContentId();
            } catch (InvalidArtifactTypeException ex) {
                //If no artifact type can be inferred, throw invalid schema ex
                throw new UnprocessableEntityException(ex.getMessage());
            }
        }

        int id = converter.convertUnsigned(sid);
        return new SchemaId(id);
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema getSchemaByVersion(String subject, String version, String groupId, Boolean deleted) throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        return getSchema(groupId, subject, version, fdeleted);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public int deleteSchemaVersion(String subject, String versionString, Boolean permanent, String groupId) throws Exception {
        try {
            if (doesArtifactExist(subject, groupId)) {
                final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;

                return VersionUtil.toInteger(parseVersionString(subject, versionString, groupId, version -> {
                    List<Long> globalIdsReferencingSchema = storage.getGlobalIdsReferencingArtifactVersion(groupId, subject, version);
                    ArtifactVersionMetaDataDto avmd = storage.getArtifactVersionMetaData(groupId, subject, version);
                    if (globalIdsReferencingSchema.isEmpty() || areAllSchemasDisabled(globalIdsReferencingSchema)) {
                        return processDeleteVersion(subject, versionString, groupId, version, fpermanent, avmd);
                    } else {
                        //There are other schemas referencing this one, it cannot be deleted.
                        throw new ReferenceExistsException(String.format("There are subjects referencing %s", subject));
                    }

                }));
            } else {
                throw new ArtifactNotFoundException(groupId, subject);
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex);
        }
    }

    private String processDeleteVersion(String subject, String versionString, String groupId, String version,
            boolean fpermanent, ArtifactVersionMetaDataDto avmd) {
        if (fpermanent) {
            if (avmd.getState().equals(VersionState.ENABLED) || avmd.getState().equals(VersionState.DEPRECATED)) {
                throw new SchemaNotSoftDeletedException(String.format("Subject %s version %s must be soft deleted first", subject, versionString));
            } else if (avmd.getState().equals(VersionState.DISABLED)) {
                storage.deleteArtifactVersion(groupId, subject, version);
            }
        } else {
            if (avmd.getState().equals(VersionState.DISABLED)) {
                throw new SchemaSoftDeletedException("Schema is already soft deleted");
            } else {
                EditableVersionMetaDataDto emd = EditableVersionMetaDataDto.builder()
                        .state(VersionState.DISABLED)
                        .build();
                storage.updateArtifactVersionMetaData(groupId, subject, version, emd);
            }
        }
        return version;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public String getSchemaOnly(String subject, String version, String groupId, Boolean deleted) throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        return getSchema(groupId, subject, version, fdeleted).getSchema();
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Long> getSchemasReferencedBy(String subject, String versionString, String groupId) throws Exception {
        if (cconfig.legacyIdModeEnabled.get()) {
            return parseVersionString(subject, versionString, groupId, version -> storage.getGlobalIdsReferencingArtifactVersion(groupId, subject, version));
        }

        return parseVersionString(subject, versionString, groupId, version -> storage.getContentIdsReferencingArtifactVersion(groupId, subject, version));
    }

    protected Schema getSchema(String groupId, String subject, String versionString, boolean deleted) {
        if (doesArtifactExist(subject, groupId) && isArtifactActive(subject, groupId)) {
            return parseVersionString(subject, versionString, groupId, version -> {
                ArtifactVersionMetaDataDto amd = storage.getArtifactVersionMetaData(groupId, subject, version);
                if (amd.getState() != VersionState.DISABLED || deleted) {
                    StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(groupId, subject, amd.getVersion());
                    return converter.convert(subject, storedArtifact, amd.getArtifactType());
                } else {
                    throw new VersionNotFoundException(groupId, subject, version);
                }
            });
        } else {
            throw new ArtifactNotFoundException(groupId, subject);
        }
    }
}
