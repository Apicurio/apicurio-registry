package io.apicurio.registry.ccompat.rest.v7.impl;

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
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.utils.VersionUtil;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_VERSION;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {

    @Inject
    ApiConverter converter;

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Integer> listVersions(String subject, String groupId, Boolean deleted) throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);

        List<Integer> rval;
        if (fdeleted) {
            rval = storage
                    .getArtifactVersions(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                            RetrievalBehavior.NON_DRAFT_STATES)
                    .stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted()
                    .collect(Collectors.toList());
        } else {
            rval = storage
                    .getArtifactVersions(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                            RetrievalBehavior.ACTIVE_STATES)
                    .stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted()
                    .collect(Collectors.toList());
        }
        if (rval.isEmpty()) {
            throw new ArtifactNotFoundException(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        }
        return rval;
    }

    @Override
    @Audited(extractParameters = { "0", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public SchemaId register(String subject, SchemaInfo request, Boolean normalize, String groupId)
            throws Exception {
        final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
        final GA ga = getGA(groupId, subject);

        // Check to see if this content is already registered - return the global ID of that content
        // if it exists. If not, then register the new content.
        long sid = -1;
        boolean idFound = false;
        if (null == request) {
            throw new UnprocessableEntityException("The schema provided is null.");
        }

        final Map<String, TypedContent> resolvedReferences = resolveReferences(request.getReferences());

        try {
            ArtifactVersionMetaDataDto dto = lookupSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                    request.getSchema(), request.getReferences(), request.getSchemaType(), fnormalize);
            if (dto.getState().equals(VersionState.DISABLED)) {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
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
                TypedContent typedSchemaContent = TypedContent.create(schemaContent, contentType);

                // We validate the schema at creation time by inferring the type from the content
                final String artifactType = ArtifactTypeUtil.determineArtifactType(typedSchemaContent, null,
                        resolvedReferences, factory);
                if (request.getSchemaType() != null && !artifactType.equals(request.getSchemaType())) {
                    throw new UnprocessableEntityException(
                            String.format("Given schema is not from type: %s", request.getSchemaType()));
                }

                ArtifactVersionMetaDataDto artifactMeta = createOrUpdateArtifact(ga.getRawArtifactId(),
                        request.getSchema(), artifactType, request.getReferences(),
                        ga.getRawGroupIdWithNull());
                sid = cconfig.legacyIdModeEnabled.get() ? artifactMeta.getGlobalId()
                    : artifactMeta.getContentId();
            } catch (InvalidArtifactTypeException ex) {
                // If no artifact type can be inferred, throw invalid schema ex
                throw new UnprocessableEntityException(ex.getMessage());
            }
        }

        int id = converter.convertUnsigned(sid);
        return new SchemaId(id);
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema getSchemaByVersion(String subject, String version, String groupId, Boolean deleted)
            throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);
        return getSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version, fdeleted);
    }

    @Override
    @Audited(extractParameters = { "0", KEY_ARTIFACT_ID, "1", KEY_VERSION })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public int deleteSchemaVersion(String subject, String versionString, Boolean permanent, String groupId)
            throws Exception {
        final GA ga = getGA(groupId, subject);
        try {
            if (doesArtifactExist(ga.getRawArtifactId(), ga.getRawGroupIdWithNull())) {
                final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;

                return VersionUtil.toInteger(parseVersionString(ga.getRawArtifactId(), versionString,
                        ga.getRawGroupIdWithNull(), version -> {
                            List<Long> globalIdsReferencingSchema = storage
                                    .getGlobalIdsReferencingArtifactVersion(ga.getRawGroupIdWithNull(),
                                            ga.getRawArtifactId(), version);
                            ArtifactVersionMetaDataDto avmd = storage.getArtifactVersionMetaData(
                                    ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version);
                            if (globalIdsReferencingSchema.isEmpty()
                                    || areAllSchemasDisabled(globalIdsReferencingSchema)) {
                                return processDeleteVersion(ga.getRawArtifactId(), versionString,
                                        ga.getRawGroupIdWithNull(), version, fpermanent, avmd);
                            } else {
                                // There are other schemas referencing this one, it cannot be deleted.
                                throw new ReferenceExistsException(String
                                        .format("There are subjects referencing %s", ga.getRawArtifactId()));
                            }

                        }));
            } else {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex);
        }
    }

    private String processDeleteVersion(String artifactId, String versionString, String groupId,
            String version, boolean fpermanent, ArtifactVersionMetaDataDto avmd) {
        if (fpermanent) {
            if (avmd.getState().equals(VersionState.ENABLED)
                    || avmd.getState().equals(VersionState.DEPRECATED)) {
                throw new SchemaNotSoftDeletedException(String.format(
                        "Subject %s version %s must be soft deleted first", artifactId, versionString));
            } else if (avmd.getState().equals(VersionState.DISABLED)) {
                storage.deleteArtifactVersion(groupId, artifactId, version);
            }
        } else {
            if (avmd.getState().equals(VersionState.DISABLED)) {
                throw new SchemaSoftDeletedException("Schema is already soft deleted");
            } else {
                storage.updateArtifactVersionState(groupId, artifactId, version, VersionState.DISABLED,
                        false);
            }
        }
        return version;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public String getSchemaOnly(String subject, String version, String groupId, Boolean deleted)
            throws Exception {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);
        return getSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version, fdeleted).getSchema();
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Long> getSchemasReferencedBy(String subject, String versionString, String groupId)
            throws Exception {
        final GA ga = getGA(groupId, subject);
        if (cconfig.legacyIdModeEnabled.get()) {
            return parseVersionString(ga.getRawArtifactId(), versionString, ga.getRawGroupIdWithNull(),
                    version -> storage.getGlobalIdsReferencingArtifactVersion(ga.getRawGroupIdWithNull(),
                            ga.getRawArtifactId(), version));
        }

        return parseVersionString(ga.getRawArtifactId(), versionString, ga.getRawGroupIdWithNull(),
                version -> storage.getContentIdsReferencingArtifactVersion(ga.getRawGroupIdWithNull(),
                        ga.getRawArtifactId(), version));
    }

    protected Schema getSchema(String groupId, String artifactId, String versionString, boolean deleted) {
        if (doesArtifactExist(artifactId, groupId) && isArtifactActive(artifactId, groupId)) {
            return parseVersionString(artifactId, versionString, groupId, version -> {
                ArtifactVersionMetaDataDto amd = storage.getArtifactVersionMetaData(groupId, artifactId,
                        version);
                if (amd.getState() != VersionState.DISABLED || deleted) {
                    StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(groupId,
                            artifactId, amd.getVersion());
                    return converter.convert(artifactId, storedArtifact, amd.getArtifactType());
                } else {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            });
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }
}
