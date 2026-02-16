package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.MethodMetadata;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.cache.strategy.interceptor.EntityIdContentCache;
import io.apicurio.registry.rest.cache.strategy.EntityIdContentCacheStrategy;
import io.apicurio.registry.rest.impl.shared.CommonResourceOperations;
import io.apicurio.registry.rest.v3.IdsResource;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.registry.rest.MethodParameterKeys.MPK_ENTITY_ID;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_REF_TYPE;
import static io.apicurio.registry.rest.cache.HttpCaching.caching;
import static io.apicurio.registry.rest.headers.Headers.checkIfDeprecated;
import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.recursivelyResolveReferenceContentIds;
import static io.apicurio.registry.utils.Cell.cellWithLoader;

@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class IdsResourceImpl extends AbstractResourceImpl implements IdsResource {

    @Inject
    CommonResourceOperations common;

    @Inject
    RestConfig restConfig;

    @Inject
    HttpHeaders _ignored; // Do not remove, ensures it is available for dynamic CDI injection.

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#getContentById(long)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID})
    @EntityIdContentCache
    public Response getContentById(long contentId) {
        ContentWrapperDto dto = storage.getContentById(contentId);
        boolean isEmptyContent = ContentTypes.isEmptyContentType(dto.getContentType());
        boolean isDraft = dto.getContentHash() != null && dto.getContentHash().startsWith("draft:");
        if (isEmptyContent || isDraft && !restConfig.isDraftProductionModeEnabled()) {
            throw new ContentNotFoundException(contentId);
        }
        return Response.ok().entity(dto.getContent())
                .type(ArtifactMediaTypes.BINARY).build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#getContentByGlobalId(long, HandleReferencesType, Boolean)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public Response getContentByGlobalId(long globalId, HandleReferencesType references,
                                         Boolean returnArtifactType) {

        if (references == null) {
            // Check if admin has configured a default reference handling behavior
            java.util.Optional<String> configuredDefault = restConfig.getDefaultReferenceHandling();
            if (configuredDefault.isPresent() && !configuredDefault.get().trim().isEmpty()) {
                references = HandleReferencesType.fromValue(configuredDefault.get());
            } else {
                // No configuration - use existing default (no behavior change)
                references = HandleReferencesType.PRESERVE;
            }
        }

        final var artifactCell = cellWithLoader(() -> storage.getArtifactVersionContent(globalId));

        caching(
                EntityIdContentCacheStrategy.builder()
                        .entityId(globalId)
                        .references(references)
                        .referenceTreeContentIds(() -> recursivelyResolveReferenceContentIds(artifactCell.get(),
                                ref -> storage.getArtifactVersionContent(ref.getGroupId(), ref.getArtifactId(), ref.getVersion())
                        ))
                        .returnArtifactType(returnArtifactType)
                        .build()
        ).prepare();

        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(globalId);
        if (VersionState.DISABLED.equals(metaData.getState())
                || (VersionState.DRAFT.equals(metaData.getState())) && !restConfig.isDraftProductionModeEnabled()) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        }

        boolean isEmptyContent = ContentTypes.isEmptyContentType(artifactCell.get().getContentType());
        if (isEmptyContent) {
            throw new ContentNotFoundException(artifactCell.get().getContentId());
        }

        TypedContent contentToReturn = TypedContent.create(artifactCell.get().getContent(), artifactCell.get().getContentType());
        contentToReturn = handleContentReferences(references, metaData.getArtifactType(), contentToReturn,
                artifactCell.get().getReferences());

        var builder = Response.ok().entity(contentToReturn.getContent())
                .type(contentToReturn.getContentType());
        if (returnArtifactType != null && returnArtifactType) {
            builder.header("X-Registry-ArtifactType", metaData.getArtifactType());
        }
        checkIfDeprecated(metaData::getState, null, metaData.getArtifactId(), metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#getContentByHash(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID})
    @EntityIdContentCache
    public Response getContentByHash(String contentHash) {
        ContentHandle content = storage.getContentByHash(contentHash).getContent();
        return Response.ok(content, ArtifactMediaTypes.BINARY).build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#referencesByContentHash(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID})
    @EntityIdContentCache
    public List<ArtifactReference> referencesByContentHash(String contentHash) {
        return common.getReferencesByContentHash(contentHash, V3ApiUtil::referenceDtoToReference);
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#referencesByContentId(long)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID})
    @EntityIdContentCache
    public List<ArtifactReference> referencesByContentId(long contentId) {
        ContentWrapperDto artifact = storage.getContentById(contentId);
        return artifact.getReferences().stream().map(V3ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v3.IdsResource#referencesByGlobalId(long,
     * io.apicurio.registry.types.ReferenceType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    @MethodMetadata(extractParameters = {"0", MPK_ENTITY_ID, "1", MPK_REF_TYPE})
    @EntityIdContentCache(refTypeParam = MPK_REF_TYPE)
    public List<ArtifactReference> referencesByGlobalId(long globalId, ReferenceType refType) {
        if (refType == ReferenceType.OUTBOUND || refType == null) {
            StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(globalId);
            return artifact.getReferences().stream().map(V3ApiUtil::referenceDtoToReference)
                    .collect(Collectors.toList());
        } else {
            ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(globalId);
            return storage
                    .getInboundArtifactReferences(vmd.getGroupId(), vmd.getArtifactId(), vmd.getVersion())
                    .stream().map(V3ApiUtil::referenceDtoToReference).collect(Collectors.toList());
        }
    }
}
