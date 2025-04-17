package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.error.ReferenceExistsException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SchemaSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.SubjectsResource;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.Schema;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaId;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactStateException;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.storage.error.InvalidVersionStateException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.utils.VersionUtil;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_VERSION;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SubjectsResourceImpl extends AbstractResource implements SubjectsResource {

    private final Cache<GA, Lock> subjectLocks = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS) // Evict locks after 1 hour of inactivity
            .maximumSize(10000) // Limit the cache size to 10,000 locks
            .build();

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> getSubjects(String subjectPrefix, Boolean deleted, String groupId) {
        // Since contexts are not supported, subjectPrefix is not used
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        Set<SearchFilter> filters = new HashSet<>();
        if (!cconfig.groupConcatEnabled) {
            filters.add(SearchFilter.ofGroupId(groupId));
        }
        if (!fdeleted) {
            filters.add(SearchFilter.ofState(VersionState.DISABLED).negated());
        }
        ArtifactSearchResultsDto searchResults = storage.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.asc, 0, cconfig.maxSubjects.get());
        Function<SearchedArtifactDto, String> toSubject = SearchedArtifactDto::getArtifactId;
        if (cconfig.groupConcatEnabled) {
            toSubject = (dto) -> toSubjectWithGroupConcat(dto);
        }

        return searchResults.getArtifacts().stream()
                .filter(saDto -> isCcompatManagedType(saDto.getArtifactType())).map(toSubject)
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema lookupSchemaByContent(String subject, Boolean normalize, String format, String groupId, Boolean deleted,
                                        RegisterSchemaRequest request) {
        GA ga = getGA(groupId, subject);

        if (doesArtifactExist(ga.getRawArtifactId(), ga.getRawGroupIdWithNull())) {
            final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
            final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;

            try {
                ArtifactVersionMetaDataDto amd;
                amd = lookupSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), request.getSchema(),
                        request.getReferences(), request.getSchemaType(), fnormalize);
                if (amd.getState() != VersionState.DISABLED || fdeleted) {
                    StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(
                            ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), amd.getVersion());
                    return converter.convert(ga.getRawArtifactId(), storedArtifact);
                }
                else {
                    throw new SchemaNotFoundException(
                            String.format("The given schema does not match any schema under the subject %s",
                                    ga.getRawArtifactId()));
                }
            }
            catch (ArtifactNotFoundException anf) {
                throw new SchemaNotFoundException(
                        String.format("The given schema does not match any schema under the subject %s",
                                ga.getRawArtifactId()));
            }
        }
        else {
            // If the artifact does not exist there is no need for looking up the schema, just fail.
            throw new ArtifactNotFoundException(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        }
    }

    @Override
    @Audited(extractParameters = { "0", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public List<BigInteger> deleteSubject(String subject, Boolean permanent, String groupId) {
        GA ga = getGA(groupId, subject);

        // This will throw an exception if the artifact does not exist.
        storage.getArtifactMetaData(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());

        final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;
        if (fpermanent) {
            return deleteSubjectPermanent(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        }
        else if (isArtifactActive(ga.getRawArtifactId(), ga.getRawGroupIdWithNull())) {
            return deleteSubjectVersions(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        }
        else {
            // The artifact exist, it's in DISABLED state but the delete request is set to not permanent,
            // throw ex.
            throw new SubjectSoftDeletedException(
                    String.format("Subject %s is in soft deleted state.", ga.getRawArtifactId()));
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<BigInteger> getSubjectVersions(String subject, String groupId, Boolean deleted) {
        final GA ga = getGA(groupId, subject);
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;

        List<BigInteger> rval;
        if (fdeleted) {
            rval = storage
                    .getArtifactVersions(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                            RegistryStorage.RetrievalBehavior.NON_DRAFT_STATES)
                    .stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted()
                    .collect(Collectors.toList());
        }
        else {
            rval = storage
                    .getArtifactVersions(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                            RegistryStorage.RetrievalBehavior.ACTIVE_STATES)
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
    public SchemaId registerSchemaUnderSubject(String subject, Boolean normalize, String format, String groupId, RegisterSchemaRequest request) {
        final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
        final GA ga = getGA(groupId, subject);

        if (null == request) {
            throw new UnprocessableEntityException("The schema provided is null.");
        }

        Lock lock;
        try {
            // Get or create a lock for the specific subject (GA) using the cache
            // ReentrantLock::new is called only if the key 'ga' is not already present
            lock = subjectLocks.get(ga, ReentrantLock::new);
        } catch (ExecutionException e) {
            // Handle exception during lock creation if necessary
            log.error("Error creating lock for subject: " + ga, e.getCause());
            throw new InternalServerErrorException("Error processing request", e.getCause());
        }

        lock.lock(); // Acquire the lock

        try {
            final Map<String, TypedContent> resolvedReferences = resolveReferences(request.getReferences());
            long sid = -1;

            try {
                // Try to find an existing, active version with the same content
                ArtifactVersionMetaDataDto existingDto = lookupSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                        request.getSchema(), request.getReferences(), request.getSchemaType(), fnormalize);

                // lookupSchema throws ArtifactNotFoundException if not found or if the found version is DISABLED.
                // If we reach here, an active version was found.
                sid = cconfig.legacyIdModeEnabled.get() ? existingDto.getGlobalId() : existingDto.getContentId();

            } catch (ArtifactNotFoundException nfe) {
                // Schema not found or the only matching version is disabled, proceed to create/update
                ArtifactVersionMetaDataDto newOrUpdatedDto = registerNewSchemaVersion(ga, request, fnormalize, resolvedReferences);
                sid = cconfig.legacyIdModeEnabled.get() ? newOrUpdatedDto.getGlobalId() : newOrUpdatedDto.getContentId();
            }

            BigInteger id = converter.convertUnsigned(sid);
            SchemaId schemaId = new SchemaId();
            schemaId.setId(id.intValue());
            return schemaId;
        } finally {
            lock.unlock(); // Release the lock in the finally block
        }
    }

    private ArtifactVersionMetaDataDto registerNewSchemaVersion(GA ga, RegisterSchemaRequest request, boolean fnormalize, Map<String, TypedContent> resolvedReferences) {
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

            return createOrUpdateArtifact(ga.getRawArtifactId(),
                    request.getSchema(), artifactType, request.getReferences(),
                    ga.getRawGroupIdWithNull(), fnormalize);
        } catch (InvalidArtifactTypeException ex) {
            // If no artifact type can be inferred, throw invalid schema ex
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema getSchemaVersion(String subject, String version, String format, String groupId, Boolean deleted) {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);
        return getSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version, fdeleted);
    }

    @Override
    @Audited(extractParameters = { "0", KEY_ARTIFACT_ID, "1", KEY_VERSION })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public BigInteger deleteSchemaVersion(String subject, String versionString, Boolean permanent, String groupId) {
        final GA ga = getGA(groupId, subject);
        try {
            if (doesArtifactExist(ga.getRawArtifactId(), ga.getRawGroupIdWithNull())) {
                final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;

                return BigInteger.valueOf(VersionUtil.toLong(parseVersionString(ga.getRawArtifactId(), versionString,
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
                            }
                            else {
                                // There are other schemas referencing this one, it cannot be deleted.
                                throw new ReferenceExistsException(String
                                        .format("There are subjects referencing %s", ga.getRawArtifactId()));
                            }

                        })));
            }
            else {
                throw new ArtifactNotFoundException(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
            }
        }
        catch (IllegalArgumentException ex) {
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
            }
            else if (avmd.getState().equals(VersionState.DISABLED)) {
                storage.deleteArtifactVersion(groupId, artifactId, version);
            }
        }
        else {
            if (avmd.getState().equals(VersionState.DISABLED)) {
                throw new SchemaSoftDeletedException("Schema is already soft deleted");
            }
            else {
                storage.updateArtifactVersionState(groupId, artifactId, version, VersionState.DISABLED,
                        false);
            }
        }
        return version;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public String getSchemaVersionContent(String subject, String version, String format, String groupId, Boolean deleted) {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final GA ga = getGA(groupId, subject);
        return getSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), version, fdeleted).getSchema();
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<BigInteger> getReferencedBy(String subject, String versionString, String groupId) {
        final GA ga = getGA(groupId, subject);
        if (cconfig.legacyIdModeEnabled.get()) {
            return parseVersionString(ga.getRawArtifactId(), versionString, ga.getRawGroupIdWithNull(),
                    version -> storage.getGlobalIdsReferencingArtifactVersion(ga.getRawGroupIdWithNull(),
                            ga.getRawArtifactId(), version)).stream().map(BigInteger::valueOf).collect(Collectors.toList());
        }

        return parseVersionString(ga.getRawArtifactId(), versionString, ga.getRawGroupIdWithNull(),
                version -> storage.getContentIdsReferencingArtifactVersion(ga.getRawGroupIdWithNull(),
                        ga.getRawArtifactId(), version)).stream().map(BigInteger::valueOf).collect(Collectors.toList());
    }

    @Override
    public Schema getSubjectMetadata(String subject, String key, String value, String format, Boolean deleted, String xRegistryGroupId) {
        //TODO not implemented
        return null;
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
                }
                else {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            });
        }
        else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    private List<BigInteger> deleteSubjectPermanent(String groupId, String artifactId) {
        if (isArtifactActive(artifactId, groupId)) {
            throw new SubjectNotSoftDeletedException(
                    String.format("Subject %s must be soft deleted first", artifactId));
        }
        else {
            return storage.deleteArtifact(groupId, artifactId).stream().map(VersionUtil::toInteger)
                    .map(converter::convertUnsigned).collect(Collectors.toList());
        }
    }

    // Deleting artifact versions means updating all the versions status to DISABLED.
    private List<BigInteger> deleteSubjectVersions(String groupId, String artifactId) {
        List<String> deletedVersions = storage.getArtifactVersions(groupId, artifactId);
        try {
            deletedVersions.forEach(version -> storage.updateArtifactVersionState(groupId, artifactId,
                    version, VersionState.DISABLED, false));
        }
        catch (InvalidArtifactStateException | InvalidVersionStateException ignored) {
            log.warn("Invalid artifact state transition", ignored);
        }
        return deletedVersions.stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted()
                .collect(Collectors.toList());
    }
}
