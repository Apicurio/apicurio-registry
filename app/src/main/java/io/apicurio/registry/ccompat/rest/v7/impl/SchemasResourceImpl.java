package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.ccompat.rest.v7.SchemasResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.interceptor.Interceptors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SchemasResourceImpl extends AbstractResource implements SchemasResource {

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<SchemaInfo> getSchemas(String subjectPrefix, Boolean deleted, Boolean latestOnly,
            Integer offset, Integer limit) {
        final boolean fdeleted = deleted != null && deleted;
        final boolean flatestOnly = latestOnly != null && latestOnly;
        final int effectiveOffset = offset != null ? offset : 0;
        final int effectiveLimit = (limit != null && limit > 0) ? limit : cconfig.maxSubjects.get();

        Set<SearchFilter> filters = new HashSet<>();
        if (!fdeleted) {
            filters.add(SearchFilter.ofState(VersionState.DISABLED).negated());
        }

        ArtifactSearchResultsDto searchResults = storage.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.asc, effectiveOffset, effectiveLimit);

        List<SchemaInfo> schemas = new ArrayList<>();
        for (var artifact : searchResults.getArtifacts()) {
            if (!isCcompatManagedType(artifact.getArtifactType())) {
                continue;
            }

            if (flatestOnly) {
                // Only get the latest version
                try {
                    String latestVersion = getLatestArtifactVersionForSubject(artifact.getArtifactId(),
                            artifact.getGroupId());
                    StoredArtifactVersionDto content = storage.getArtifactVersionContent(
                            artifact.getGroupId(), artifact.getArtifactId(), latestVersion);
                    schemas.add(converter.convert(content.getContent(), artifact.getArtifactType(),
                            content.getReferences()));
                } catch (Exception e) {
                    // Skip if we can't get the version
                }
            } else {
                // Get all versions
                List<String> versions = storage.getArtifactVersions(artifact.getGroupId(),
                        artifact.getArtifactId());
                for (String version : versions) {
                    try {
                        ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(
                                artifact.getGroupId(), artifact.getArtifactId(), version);
                        if (!fdeleted && vmd.getState() == VersionState.DISABLED) {
                            continue;
                        }
                        StoredArtifactVersionDto content = storage.getArtifactVersionContent(
                                artifact.getGroupId(), artifact.getArtifactId(), version);
                        schemas.add(converter.convert(content.getContent(), artifact.getArtifactType(),
                                content.getReferences()));
                    } catch (Exception e) {
                        // Skip if we can't get the version
                    }
                }
            }
        }
        return schemas;
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public SchemaInfo getSchema(int id, String subject, String groupId) {
        ContentHandle contentHandle;
        String contentType;
        List<ArtifactReferenceDto> references;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactVersionDto artifactVersion = storage.getArtifactVersionContent(id);
            contentHandle = artifactVersion.getContent();
            contentType = artifactVersion.getContentType();
            references = artifactVersion.getReferences();
        } else {
            ContentWrapperDto contentWrapper = storage.getContentById(id);
            contentHandle = contentWrapper.getContent();
            contentType = contentWrapper.getContentType();
            references = contentWrapper.getReferences();
        }
        TypedContent typedContent = TypedContent.create(contentHandle, contentType);
        return converter.convert(contentHandle, ArtifactTypeUtil.determineArtifactType(typedContent, null,
                storage.resolveReferences(references), factory), references);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> getRegisteredTypes() {
        return Arrays.asList(ArtifactType.JSON, ArtifactType.PROTOBUF, ArtifactType.AVRO);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public List<SubjectVersion> getSubjectVersions(int id, Boolean fdeleted) {
        boolean deleted = fdeleted != null && fdeleted;
        if (cconfig.legacyIdModeEnabled.get()) {
            ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData((long) id);
            return Collections
                    .singletonList(converter.convert(metaData.getArtifactId(), metaData.getVersionOrder()));
        }
        return storage.getArtifactVersionsByContentId(id).stream()
                .filter(versionMetaData -> deleted || versionMetaData.getState() != VersionState.DISABLED)
                .map(versionMetaData -> converter.convert(versionMetaData.getArtifactId(),
                        versionMetaData.getVersionOrder()))
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public List<String> getSubjectsBySchemaId(int id, Boolean deleted) {
        boolean fdeleted = deleted != null && deleted;
        if (cconfig.legacyIdModeEnabled.get()) {
            ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData((long) id);
            return Collections.singletonList(metaData.getArtifactId());
        }
        return storage.getArtifactVersionsByContentId(id).stream()
                .filter(versionMetaData -> fdeleted || versionMetaData.getState() != VersionState.DISABLED)
                .map(ArtifactVersionMetaDataDto::getArtifactId).distinct().collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public String getSchemaString(int id, String subject) {
        ContentHandle contentHandle;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactVersionDto artifactVersion = storage.getArtifactVersionContent(id);
            contentHandle = artifactVersion.getContent();
        } else {
            ContentWrapperDto contentWrapper = storage.getContentById(id);
            contentHandle = contentWrapper.getContent();
        }
        return contentHandle.content();
    }
}
