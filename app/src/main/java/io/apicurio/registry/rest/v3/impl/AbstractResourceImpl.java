package io.apicurio.registry.rest.v3.impl;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.StringUtil;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_API;

public abstract class AbstractResourceImpl {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Context
    HttpServletRequest request;

    @ConfigProperty(name = "apicurio.apis.v3.base-href", defaultValue = "_")
    @Info(category = CATEGORY_API, description = "API base href (URI)", availableSince = "2.5.0.Final")
    String apiBaseHref;


    /**
     * Handle the content references based on the value of "HandleReferencesType" - this can either mean we
     * need to fully dereference the content, or we need to rewrite the references, or we do nothing.
     */
    protected TypedContent handleContentReferences(HandleReferencesType referencesType, String artifactType,
            TypedContent content, List<ArtifactReferenceDto> references) {
        if (!references.isEmpty()) {
            if (referencesType == HandleReferencesType.DEREFERENCE) {
                ArtifactTypeUtilProvider artifactTypeProvider = factory.getArtifactTypeProvider(artifactType);

                if (artifactTypeProvider.supportsReferencesWithContext()) {
                    RegistryContentUtils.RewrittenContentHolder rewrittenContent = RegistryContentUtils
                            .recursivelyResolveReferencesWithContext(factory, content, artifactType, references,
                                    storage::getContentByReference);

                    content = artifactTypeProvider.getContentDereferencer().dereference(
                            rewrittenContent.getRewrittenContent(), rewrittenContent.getResolvedReferences());
                } else {
                    content = artifactTypeProvider.getContentDereferencer().dereference(content,
                            RegistryContentUtils.recursivelyResolveReferences(references,
                                    storage::getContentByReference));
                }
            } else if (referencesType == HandleReferencesType.REWRITE) {
                ArtifactTypeUtilProvider artifactTypeProvider = factory.getArtifactTypeProvider(artifactType);
                ContentDereferencer contentDereferencer = artifactTypeProvider.getContentDereferencer();
                Map<String, String> resolvedReferenceUrls = resolveReferenceUrls(references);
                content = contentDereferencer.rewriteReferences(content, resolvedReferenceUrls);
            }
        }
        return content;
    }

    /**
     * Convert the list of references into a list of REST API URLs that point to the content. This means that
     * we generate a REST API URL from the GAV (groupId, artifactId, version) information found in each
     * reference.
     *
     * @param references
     */
    protected Map<String, String> resolveReferenceUrls(List<ArtifactReferenceDto> references) {
        Map<String, String> rval = new HashMap<>();
        for (ArtifactReferenceDto reference : references) {
            String resolvedReferenceUrl = resolveReferenceUrl(reference);
            if (reference.getName().contains("#")) {
                JsonPointerExternalReference jpRef = new JsonPointerExternalReference(reference.getName());
                resolvedReferenceUrl = resolvedReferenceUrl + jpRef.getComponent();
            }
            if (resolvedReferenceUrl != null) {
                rval.put(reference.getName(), resolvedReferenceUrl);
            }
        }
        return rval;
    }

    /**
     * Convert a single artifact reference to a REST API URL. This means that we generate a REST API URL from
     * the GAV (groupId, artifactId, version) information found in the reference.
     *
     * @param reference
     */
    protected String resolveReferenceUrl(ArtifactReferenceDto reference) {
        URI baseHref = null;
        try {
            if (!"_".equals(apiBaseHref)) {
                baseHref = new URI(apiBaseHref);
            } else {
                baseHref = getApiBaseHrefFromXForwarded(request);
                if (baseHref == null) {
                    baseHref = getApiBaseHrefFromRequest(request);
                }
            }
        } catch (URISyntaxException e) {
            this.log.error("Error trying to determine the baseHref of the REST API.", e);
            return null;
        }

        if (baseHref == null) {
            this.log.warn("Failed to determine baseHref for the REST API.");
            return null;
        }

        String path = String.format("/apis/registry/v3/groups/%s/artifacts/%s/versions/%s/content?references=REWRITE",
                URLEncoder.encode(new GroupId(reference.getGroupId()).getRawGroupIdWithDefaultString(), StandardCharsets.UTF_8),
                URLEncoder.encode(reference.getArtifactId(), StandardCharsets.UTF_8),
                URLEncoder.encode(reference.getVersion(), StandardCharsets.UTF_8));
        return baseHref.resolve(path).toString();
    }

    /**
     * Resolves a host name from the information found in X-Forwarded-Host and X-Forwarded-Proto.
     */
    private static URI getApiBaseHrefFromXForwarded(HttpServletRequest request) throws URISyntaxException {
        String fproto = request.getHeader("X-Forwarded-Proto");
        String fhost = request.getHeader("X-Forwarded-Host");
        if (!StringUtil.isEmpty(fproto) && !StringUtil.isEmpty(fhost)) {
            return new URI(fproto + "://" + fhost);
        } else {
            return null;
        }
    }

    /**
     * Resolves a host name from the request information.
     */
    private static URI getApiBaseHrefFromRequest(HttpServletRequest request) throws URISyntaxException {
        String requestUrl = request.getRequestURL().toString();
        URI requestUri = new URI(requestUrl);
        return requestUri.resolve("/");
    }

}
