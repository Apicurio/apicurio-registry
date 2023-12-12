package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.ccompat.rest.v7.SchemasResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.interceptor.Interceptors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SchemasResourceImpl extends AbstractResource implements SchemasResource {

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public SchemaInfo getSchema(int id, String subject, String groupId) {
        ContentHandle contentHandle;
        List<ArtifactReferenceDto> references;
        if (cconfig.legacyIdModeEnabled.get()) {
            StoredArtifactDto artifactVersion = storage.getArtifactVersion(id);
            contentHandle = artifactVersion.getContent();
            references = artifactVersion.getReferences();
        } else {
            ContentWrapperDto contentWrapper = storage.getArtifactByContentId(id);
            contentHandle = storage.getArtifactByContentId(id).getContent();
            references = contentWrapper.getReferences();
            List<ArtifactMetaDataDto> artifacts = storage.getArtifactVersionsByContentId(id);
            if (artifacts == null || artifacts.isEmpty()) {
                // the contentId points to an orphaned content
                throw new ArtifactNotFoundException("ContentId: " + id);
            }
        }
        return converter.convert(contentHandle, ArtifactTypeUtil.determineArtifactType(contentHandle, null,
                null, storage.resolveReferences(references), factory.getAllArtifactTypes()), references);
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
            ArtifactMetaDataDto artifactMetaData = storage.getArtifactMetaData(id);
            return Collections.singletonList(
                    converter.convert(artifactMetaData.getId(), artifactMetaData.getVersionId()));
        }

        return storage.getArtifactVersionsByContentId(id).stream()
                .filter(artifactMetaData -> deleted
                        || isArtifactActive(artifactMetaData.getId(), artifactMetaData.getGroupId(), DEFAULT))
                .map(artifactMetaData -> converter.convert(artifactMetaData.getId(),
                        artifactMetaData.getVersionId()))
                .collect(Collectors.toList());
    }
}
