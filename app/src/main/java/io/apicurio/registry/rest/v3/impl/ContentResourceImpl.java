package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v3.ContentResource;
import io.apicurio.registry.rest.v3.beans.ContentCreateResponse;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

import java.io.InputStream;
import java.util.Collections;

@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ContentResourceImpl extends AbstractResourceImpl implements ContentResource {

    /**
     * @see io.apicurio.registry.rest.v3.ContentResource#uploadContent(String, InputStream)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public ContentCreateResponse uploadContent(String xRegistryArtifactType, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        String contentType = getContentTypeFromRequest();

        TypedContent typedContent = TypedContent.create(content, contentType);
        String artifactType = ArtifactTypeUtil.determineArtifactType(typedContent, xRegistryArtifactType,
                factory);

        ContentWrapperDto contentDto = ContentWrapperDto.builder()
                .content(content)
                .contentType(contentType)
                .references(Collections.emptyList())
                .build();

        long contentId = storage.createOrGetContent(artifactType, contentDto);

        ContentCreateResponse response = new ContentCreateResponse();
        response.setContentId(contentId);
        return response;
    }

    /**
     * Extracts the content type from the HTTP request's Content-Type header.
     */
    private String getContentTypeFromRequest() {
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(";")).trim();
        }
        return contentType;
    }
}
