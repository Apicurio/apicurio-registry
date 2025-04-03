package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.types.webhooks.beans.ContentAccepterRequest;

import java.util.Map;

public class ConfiguredContentAccepter extends AbstractConfiguredArtifactTypeUtil<ContentAccepter> implements ContentAccepter {

    public ConfiguredContentAccepter(HttpClientService httpClientService, ArtifactTypeConfiguration artifactType) {
        super(httpClientService, artifactType, artifactType.getContentAccepter());
    }

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return this.delegate.acceptsContent(content, resolvedReferences);
    }

    @Override
    protected ContentAccepter createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
        return new JavaClassContentAccepterDelegate(artifactType, provider);
    }

    private class JavaClassContentAccepterDelegate extends AbstractJavaClassDelegate implements ContentAccepter {
        public JavaClassContentAccepterDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            super(artifactType, provider);
        }

        @Override
        public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            return this.instance.acceptsContent(content, resolvedReferences);
        }
    }

    @Override
    protected ContentAccepter createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception {
        return new WebhookContentAccepterDelegate(artifactType, provider);
    }

    private class WebhookContentAccepterDelegate extends AbstractWebhookDelegate<ContentAccepterRequest, Boolean> implements ContentAccepter {

        protected WebhookContentAccepterDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            // Create the request payload object
            ContentAccepterRequest requestBody = new ContentAccepterRequest();
            requestBody.setTypedContent(WebhookBeanUtil.typedContentToWebhookBean(content));
            requestBody.setResolvedReferences(WebhookBeanUtil.resolvedReferenceListToWebhookBean(resolvedReferences));

            try {
                return invokeHook(requestBody, Boolean.class);
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
                return false;
            }
        }

    }
}
