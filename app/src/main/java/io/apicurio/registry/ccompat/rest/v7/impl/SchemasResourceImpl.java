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
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.interceptor.Interceptors;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SchemasResourceImpl extends AbstractResource implements SchemasResource {

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public Schema getSchemaById(BigInteger id, String format, String subject) {
        ContentHandle contentHandle;
        String contentType;
        List<ArtifactReferenceDto> references;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactVersionDto artifactVersion = storage.getArtifactVersionContent(id.longValue());
            contentHandle = artifactVersion.getContent();
            contentType = artifactVersion.getContentType();
            references = artifactVersion.getReferences();
        }
        else {
            ContentWrapperDto contentWrapper = storage.getContentById(id.longValue());
            contentHandle = contentWrapper.getContent();
            contentType = contentWrapper.getContentType();
            references = contentWrapper.getReferences();
        }
        TypedContent typedContent = TypedContent.create(contentHandle, contentType);
        return converter.convert(contentHandle,
                ArtifactTypeUtil.determineArtifactType(typedContent, null, RegistryContentUtils
                        .recursivelyResolveReferences(references, storage::getContentByReference), factory),
                references);
    }

    @Override
    public String getSchemaContentById(BigInteger id, String format, String subject) {
        ContentHandle contentHandle;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactVersionDto artifactVersion = storage.getArtifactVersionContent(id.longValue());
            contentHandle = artifactVersion.getContent();
        }
        else {
            ContentWrapperDto contentWrapper = storage.getContentById(id.longValue());
            contentHandle = contentWrapper.getContent();
        }

        return contentHandle.content();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> getSchemaTypes() {
        return Arrays.asList(ArtifactType.JSON, ArtifactType.PROTOBUF, ArtifactType.AVRO);
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
