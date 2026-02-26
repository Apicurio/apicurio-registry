package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.v7.SchemasResource;
import io.apicurio.registry.ccompat.rest.v7.beans.Schema;
import io.apicurio.registry.ccompat.rest.v7.beans.SubjectVersion;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.VersionState;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SchemasResourceImpl extends AbstractResource implements SchemasResource {

    @Inject
    SchemaFormatService formatService;

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public Schema getSchemaById(BigInteger id, String format, String subject) {
        ContentHandle contentHandle;
        List<ArtifactReferenceDto> references;
        String artifactType;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactVersionDto artifactVersion = storage.getArtifactVersionContent(id.longValue());
            contentHandle = artifactVersion.getContent();
            references = artifactVersion.getReferences();
            ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(id.longValue());
            artifactType = vmd.getArtifactType();
        } else {
            ContentWrapperDto contentWrapper = storage.getContentById(id.longValue());
            contentHandle = contentWrapper.getContent();
            references = contentWrapper.getReferences();
            List<ArtifactVersionMetaDataDto> versions = storage.getArtifactVersionsByContentId(id.longValue());
            if (versions == null || versions.isEmpty()) {
                //the contentId points to an orphaned content
                throw new ArtifactNotFoundException("ContentId: " + id);
            }
            artifactType = versions.get(0).getArtifactType();
        }

        // Apply default format if configured and no format was explicitly provided
        if (format == null || format.isBlank()) {
            java.util.Optional<String> configuredDefault = restConfig.getDefaultReferenceHandling();
            if (configuredDefault.isPresent() && !configuredDefault.get().trim().isEmpty()
                    && "DEREFERENCE".equalsIgnoreCase(configuredDefault.get())) {
                // Apply RESOLVED format for Avro and Protobuf when DEREFERENCE is configured
                if (ArtifactType.AVRO.equals(artifactType) || ArtifactType.PROTOBUF.equals(artifactType)) {
                    format = "resolved";
                }
            }
        }

        // Apply format transformation if requested
        if (format != null && !format.isBlank()) {
            Map<String, TypedContent> resolvedReferences = resolveReferenceDtos(references);
            contentHandle = formatService.applyFormat(contentHandle, artifactType, format,
                    resolvedReferences);
        }

        return converter.convert(contentHandle, artifactType, references);
    }

    @Override
    public String getSchemaContentById(BigInteger id, String format, String subject) {
        ContentHandle contentHandle;
        List<ArtifactReferenceDto> references;
        String artifactType;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactVersionDto artifactVersion = storage.getArtifactVersionContent(id.longValue());
            contentHandle = artifactVersion.getContent();
            references = artifactVersion.getReferences();
            ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(id.longValue());
            artifactType = vmd.getArtifactType();
        } else {
            ContentWrapperDto contentWrapper = storage.getContentById(id.longValue());
            contentHandle = contentWrapper.getContent();
            references = contentWrapper.getReferences();
            List<ArtifactVersionMetaDataDto> versions = storage.getArtifactVersionsByContentId(id.longValue());
            if (versions == null || versions.isEmpty()) {
                //the contentId points to an orphaned content
                throw new ArtifactNotFoundException("ContentId: " + id);
            }
            artifactType = versions.get(0).getArtifactType();
        }

        // Apply default format if configured and no format was explicitly provided
        if (format == null || format.isBlank()) {
            java.util.Optional<String> configuredDefault = restConfig.getDefaultReferenceHandling();
            if (configuredDefault.isPresent() && !configuredDefault.get().trim().isEmpty()
                    && "DEREFERENCE".equals(configuredDefault.get())) {
                // Apply RESOLVED format for Avro and Protobuf when DEREFERENCE is configured
                if (ArtifactType.AVRO.equals(artifactType) || ArtifactType.PROTOBUF.equals(artifactType)) {
                    format = "RESOLVED";
                }
            }
        }

        // Apply format transformation if requested
        if (format != null && !format.trim().isEmpty()) {
            Map<String, TypedContent> resolvedReferences = resolveReferenceDtos(references);
            contentHandle = formatService.applyFormat(contentHandle, artifactType, format,
                    resolvedReferences);
        }

        return contentHandle.content();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> getSchemaTypes() {
        return Arrays.asList(ArtifactType.JSON, ArtifactType.PROTOBUF, ArtifactType.AVRO);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<Schema> getSchemas(String subjectPrefix, Boolean deleted, Boolean latestOnly,
            BigInteger offset, BigInteger limit) {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        final boolean flatestOnly = latestOnly == null ? Boolean.FALSE : latestOnly;

        Set<SearchFilter> filters = new HashSet<>();
        if (!fdeleted) {
            filters.add(SearchFilter.ofState(VersionState.DISABLED).negated());
        }

        // Handle pagination
        int effectiveOffset = offset != null ? offset.intValue() : 0;
        int effectiveLimit = (limit != null && limit.intValue() > 0) ? limit.intValue()
                : cconfig.maxSubjects.get();

        // Search for versions to get schemas
        VersionSearchResultsDto searchResults = storage.searchVersions(filters, OrderBy.createdOn,
                OrderDirection.asc, effectiveOffset, effectiveLimit);

        List<Schema> schemas = new ArrayList<>();
        Set<Long> seenContentIds = new HashSet<>();

        for (SearchedVersionDto version : searchResults.getVersions()) {
            // Filter by subject prefix if provided
            if (subjectPrefix != null && !version.getArtifactId().startsWith(subjectPrefix)) {
                continue;
            }

            // If latestOnly, skip non-latest versions (we'd need to track per-artifact)
            // For now, just return all matching schemas

            long contentId = version.getContentId();
            if (flatestOnly && seenContentIds.contains(contentId)) {
                continue;
            }
            seenContentIds.add(contentId);

            try {
                ContentWrapperDto contentWrapper = storage.getContentById(contentId);
                Schema schema = converter.convert(contentWrapper.getContent(),
                        version.getArtifactType(), contentWrapper.getReferences());
                schemas.add(schema);
            } catch (Exception e) {
                // Skip schemas that can't be loaded
            }
        }

        return schemas;
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public List<String> getSubjectsBySchemaId(BigInteger id, Boolean deleted) {
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;

        List<ArtifactVersionMetaDataDto> versions = storage.getArtifactVersionsByContentId(id.longValue());
        if (versions == null || versions.isEmpty()) {
            throw new ArtifactNotFoundException("ContentId: " + id);
        }

        return versions.stream()
                .filter(v -> fdeleted || v.getState() != VersionState.DISABLED)
                .map(ArtifactVersionMetaDataDto::getArtifactId).distinct().collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public List<SubjectVersion> getSchemaVersionsById(BigInteger id, Boolean fdeleted) {
        boolean deleted = fdeleted != null && fdeleted;
        if (cconfig.legacyIdModeEnabled.get()) {
            ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(id.longValue());
            return Collections
                    .singletonList(converter.convert(metaData.getArtifactId(), metaData.getVersionOrder()));
        }
        return storage.getArtifactVersionsByContentId(id.longValue()).stream()
                .filter(versionMetaData -> deleted || versionMetaData.getState() != VersionState.DISABLED)
                .map(versionMetaData -> converter.convert(versionMetaData.getArtifactId(),
                        versionMetaData.getVersionOrder()))
                .collect(Collectors.toList());
    }
}
