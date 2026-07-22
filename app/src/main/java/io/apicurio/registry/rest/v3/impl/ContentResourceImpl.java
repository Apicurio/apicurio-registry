package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v3.ContentResource;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the /content sub-resource.
 */
@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ContentResourceImpl implements ContentResource {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    RegistryStorageContentUtils contentUtils;

    @Context
    HttpHeaders httpHeaders;

    /**
     * @see io.apicurio.registry.rest.v3.ContentResource#detectContentReferences(String, VersionContent)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<ArtifactReference> detectContentReferences(String artifactType, VersionContent body) {
        ContentHandle content = ContentHandle.create(body.getContent());
        String contentType = body.getContentType();
        TypedContent typedContent = TypedContent.create(content, contentType);

        // Auto-detect artifact type if not provided
        if (artifactType == null || artifactType.isBlank()) {
            artifactType = ArtifactTypeUtil.determineArtifactType(typedContent, null, factory);
        }

        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ReferenceFinder referenceFinder = provider.getReferenceFinder();
        Set<ExternalReference> externalRefs = referenceFinder.findExternalReferences(typedContent);

        return externalRefs.stream()
                .map(ref -> {
                    ArtifactReference ar = new ArtifactReference();
                    ar.setName(ref.getFullReference());
                    return ar;
                })
                .sorted(Comparator.comparing(ArtifactReference::getName))
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response canonicalizeContent(String artifactType, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException("Empty content is not allowed.");
        }
        if (!factory.getAllArtifactTypes().contains(artifactType)) {
            throw new BadRequestException("Unknown artifact type: " + artifactType);
        }
        String ct = httpHeaders.getMediaType() != null ? httpHeaders.getMediaType().toString() : null;
        TypedContent typedContent = TypedContent.create(content, ct);
        TypedContent canonicalized = contentUtils.canonicalizeContent(artifactType, typedContent,
                Map.of());
        return Response.ok(canonicalized.getContent()).type(canonicalized.getContentType()).build();
    }
}
