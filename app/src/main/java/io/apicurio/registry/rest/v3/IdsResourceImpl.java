package io.apicurio.registry.rest.v3;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.rest.v3.shared.CommonResourceOperations;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class IdsResourceImpl extends AbstractResourceImpl implements IdsResource {

    @Inject
    CommonResourceOperations common;

    private void checkIfDeprecated(Supplier<VersionState> stateSupplier, String artifactId, String version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, null, artifactId, version, builder);
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#getContentById(long)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getContentById(long contentId) {
        ContentHandle content = storage.getContentById(contentId).getContent();
        Response.ResponseBuilder builder = Response.ok(content, ArtifactMediaTypes.BINARY);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#getContentByGlobalId(long, io.apicurio.registry.rest.v3.beans.HandleReferencesType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public Response getContentByGlobalId(long globalId, HandleReferencesType references) {
        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(globalId);
        if (VersionState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        }

        if (references == null) {
            references = HandleReferencesType.PRESERVE;
        }

        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(globalId);

        TypedContent contentToReturn = TypedContent.create(artifact.getContent(), artifact.getContentType());
        contentToReturn = handleContentReferences(references, metaData.getArtifactType(), contentToReturn, artifact.getReferences());

        Response.ResponseBuilder builder = Response.ok(contentToReturn.getContent(), contentToReturn.getContentType());
        checkIfDeprecated(metaData::getState, metaData.getArtifactId(), metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#getContentByHash(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getContentByHash(String contentHash) {
        ContentHandle content = storage.getContentByHash(contentHash).getContent();
        Response.ResponseBuilder builder = Response.ok(content, ArtifactMediaTypes.BINARY);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#referencesByContentHash(java.lang.String)
     */
    @Override
    public List<ArtifactReference> referencesByContentHash(String contentHash) {
        return common.getReferencesByContentHash(contentHash);
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#referencesByContentId(long)
     */
    @Override
    public List<ArtifactReference> referencesByContentId(long contentId) {
        ContentWrapperDto artifact = storage.getContentById(contentId);
        return artifact.getReferences().stream()
                .map(V3ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#referencesByGlobalId(long, io.apicurio.registry.types.ReferenceType)
     */
    @Override
    public List<ArtifactReference> referencesByGlobalId(long globalId, ReferenceType refType) {
        if (refType == ReferenceType.OUTBOUND || refType == null) {
            StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(globalId);
            return artifact.getReferences().stream()
                    .map(V3ApiUtil::referenceDtoToReference)
                    .collect(Collectors.toList());
        } else {
            ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(globalId);
            return storage.getInboundArtifactReferences(vmd.getGroupId(), vmd.getArtifactId(), vmd.getVersion()).stream()
                    .map(V3ApiUtil::referenceDtoToReference)
                    .collect(Collectors.toList());
        }
    }
}
