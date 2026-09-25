package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.types.webhooks.beans.ReferenceFinderRequest;
import io.apicurio.registry.types.webhooks.beans.ReferenceFinderResponse;

import java.util.Set;

public class ConfiguredReferenceFinder extends AbstractConfiguredArtifactTypeUtil<ReferenceFinder> implements ReferenceFinder {

    public ConfiguredReferenceFinder(HttpClientService httpClientService, ArtifactTypeConfiguration artifactType) {
        super(httpClientService, artifactType, artifactType.getReferenceFinder());
    }

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        return this.delegate.findExternalReferences(content);
    }

    @Override
    protected ReferenceFinder createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
        return new ConfiguredReferenceFinder.JavaClassReferenceFinderDelegate(artifactType, provider);
    }

    private class JavaClassReferenceFinderDelegate extends AbstractJavaClassDelegate implements ReferenceFinder {
        public JavaClassReferenceFinderDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            super(artifactType, provider);
        }

        @Override
        public Set<ExternalReference> findExternalReferences(TypedContent content) {
            return this.instance.findExternalReferences(content);
        }
    }

    @Override
    protected ReferenceFinder createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception {
        return new ConfiguredReferenceFinder.WebhookReferenceFinderDelegate(artifactType, provider);
    }

    private class WebhookReferenceFinderDelegate extends AbstractWebhookDelegate<ReferenceFinderRequest, ReferenceFinderResponse> implements ReferenceFinder {

        protected WebhookReferenceFinderDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public Set<ExternalReference> findExternalReferences(TypedContent content) {
            ReferenceFinderRequest requestBody = createRequest(content);

            try {
                ReferenceFinderResponse responseBody = invokeHook(requestBody, ReferenceFinderResponse.class);
                return WebhookBeanUtil.externalReferencesFromWebhookBean(responseBody.getExternalReferences());
            } catch (Throwable e) {
                throw new ReferenceFinderException("Error invoking webhook for reference finder.", e);
            }
        }
    }

    private static ReferenceFinderRequest createRequest(TypedContent content) {
        ReferenceFinderRequest requestBody = new ReferenceFinderRequest();
        requestBody.setTypedContent(WebhookBeanUtil.typedContentToWebhookBean(content));
        return requestBody;
    }

}
