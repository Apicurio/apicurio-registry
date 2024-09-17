package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.error.SubjectNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectSoftDeletedException;
import io.apicurio.registry.ccompat.rest.v7.SubjectsResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactStateException;
import io.apicurio.registry.storage.error.InvalidVersionStateException;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.VersionUtil;
import jakarta.interceptor.Interceptors;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SubjectsResourceImpl extends AbstractResource implements SubjectsResource {

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> listSubjects(String subjectPrefix, Boolean deleted, String groupId) {
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
                .filter(saDto -> isCcompatManagedType(saDto.getArtifactType()))
                .map(toSubject)
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema findSchemaByContent(String subject, SchemaInfo request, Boolean normalize, String groupId,
            Boolean deleted) throws Exception {
        GA ga = getGA(groupId, subject);

        if (doesArtifactExist(ga.getRawArtifactId(), ga.getRawGroupIdWithNull())) {
            final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
            final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;

            try {
                ArtifactVersionMetaDataDto amd;
                amd = lookupSchema(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), request.getSchema(), request.getReferences(),
                        request.getSchemaType(), fnormalize);
                if (amd.getState() != VersionState.DISABLED || fdeleted) {
                    StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(ga.getRawGroupIdWithNull(),
                            ga.getRawArtifactId(), amd.getVersion());
                    return converter.convert(ga.getRawArtifactId(), storedArtifact);
                } else {
                    throw new SchemaNotFoundException(String.format(
                            "The given schema does not match any schema under the subject %s", ga.getRawArtifactId()));
                }
            } catch (ArtifactNotFoundException anf) {
                throw new SchemaNotFoundException(String
                        .format("The given schema does not match any schema under the subject %s", ga.getRawArtifactId()));
            }
        } else {
            // If the artifact does not exist there is no need for looking up the schema, just fail.
            throw new ArtifactNotFoundException(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        }
    }

    @Override
    @Audited(extractParameters = { "0", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public List<Integer> deleteSubject(String subject, Boolean permanent, String groupId) throws Exception {
        GA ga = getGA(groupId, subject);

        // This will throw an exception if the artifact does not exist.
        storage.getArtifactMetaData(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());

        final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;
        if (fpermanent) {
            return deleteSubjectPermanent(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        } else if (isArtifactActive(ga.getRawArtifactId(), ga.getRawGroupIdWithNull())) {
            return deleteSubjectVersions(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        } else {
            // The artifact exist, it's in DISABLED state but the delete request is set to not permanent,
            // throw ex.
            throw new SubjectSoftDeletedException(
                    String.format("Subject %s is in soft deleted state.", ga.getRawArtifactId()));
        }
    }

    private List<Integer> deleteSubjectPermanent(String groupId, String artifactId) {
        if (isArtifactActive(artifactId, groupId)) {
            throw new SubjectNotSoftDeletedException(
                    String.format("Subject %s must be soft deleted first", artifactId));
        } else {
            return storage.deleteArtifact(groupId, artifactId).stream().map(VersionUtil::toInteger)
                    .map(converter::convertUnsigned).collect(Collectors.toList());
        }
    }

    // Deleting artifact versions means updating all the versions status to DISABLED.
    private List<Integer> deleteSubjectVersions(String groupId, String artifactId) {
        List<String> deletedVersions = storage.getArtifactVersions(groupId, artifactId);
        try {
            EditableVersionMetaDataDto dto = EditableVersionMetaDataDto.builder().state(VersionState.DISABLED)
                    .build();
            deletedVersions.forEach(
                    version -> storage.updateArtifactVersionMetaData(groupId, artifactId, version, dto));
        } catch (InvalidArtifactStateException | InvalidVersionStateException ignored) {
            log.warn("Invalid artifact state transition", ignored);
        }
        return deletedVersions.stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted()
                .collect(Collectors.toList());
    }
}
