package io.apicurio.registry.rest.v2;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.shared.CommonResourceOperations;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class IdsResourceImpl implements IdsResource {

    @Inject
    CommonResourceOperations common;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private void checkIfDeprecated(Supplier<VersionState> stateSupplier, String artifactId, String version,
            Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, null, artifactId, version, builder);
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentById(long)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getContentById(long contentId) {
        ContentHandle content = storage.getContentById(contentId).getContent();
        Response.ResponseBuilder builder = Response.ok(content, ArtifactMediaTypes.BINARY);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentByGlobalId(long, Boolean)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public Response getContentByGlobalId(long globalId, Boolean dereference) {
        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(globalId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        }

        if (dereference == null) {
            dereference = Boolean.FALSE;
        }

        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(globalId);

        TypedContent contentToReturn = TypedContent.create(artifact.getContent(), artifact.getContentType());

        ArtifactTypeUtilProvider artifactTypeProvider = factory
                .getArtifactTypeProvider(metaData.getArtifactType());

        if (dereference && !artifact.getReferences().isEmpty()) {
            if (artifactTypeProvider.supportsReferencesWithContext()) {
                RegistryContentUtils.RewrittenContentHolder rewrittenContent = RegistryContentUtils
                        .recursivelyResolveReferencesWithContext(factory, contentToReturn, metaData.getArtifactType(),
                                artifact.getReferences(), storage::getContentByReference);

                contentToReturn = artifactTypeProvider.getContentDereferencer().dereference(
                        rewrittenContent.getRewrittenContent(), rewrittenContent.getResolvedReferences());
            } else {
                contentToReturn = artifactTypeProvider.getContentDereferencer().dereference(contentToReturn,
                        RegistryContentUtils.recursivelyResolveReferences(artifact.getReferences(),
                                storage::getContentByReference));
            }
        }

        Response.ResponseBuilder builder = Response.ok(contentToReturn.getContent(),
                contentToReturn.getContentType());
        checkIfDeprecated(metaData::getState, metaData.getArtifactId(), metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentByHash(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getContentByHash(String contentHash) {
        ContentHandle content = storage.getContentByHash(contentHash).getContent();
        Response.ResponseBuilder builder = Response.ok(content, ArtifactMediaTypes.BINARY);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#referencesByContentHash(java.lang.String)
     */
    @Override
    public List<ArtifactReference> referencesByContentHash(String contentHash) {
        return common.getReferencesByContentHash(contentHash);
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#referencesByContentId(long)
     */
    @Override
    public List<ArtifactReference> referencesByContentId(long contentId) {
        ContentWrapperDto artifact = storage.getContentById(contentId);
        return artifact.getReferences().stream().map(V2ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#referencesByGlobalId(long,
     *      io.apicurio.registry.types.ReferenceType)
     */
    @Override
    public List<ArtifactReference> referencesByGlobalId(long globalId, ReferenceType refType) {
        if (refType == ReferenceType.OUTBOUND || refType == null) {
            StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(globalId);
            return artifact.getReferences().stream().map(V2ApiUtil::referenceDtoToReference)
                    .collect(Collectors.toList());
        } else {
            ArtifactVersionMetaDataDto amd = storage.getArtifactVersionMetaData(globalId);
            return storage
                    .getInboundArtifactReferences(amd.getGroupId(), amd.getArtifactId(), amd.getVersion())
                    .stream().map(V2ApiUtil::referenceDtoToReference).collect(Collectors.toList());
        }
    }
}
