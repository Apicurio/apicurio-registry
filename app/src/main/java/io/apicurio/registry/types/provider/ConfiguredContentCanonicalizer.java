package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.webhooks.beans.ContentCanonicalizerRequest;
import io.apicurio.registry.types.webhooks.beans.ContentCanonicalizerResponse;

import java.util.Map;

public class ConfiguredContentCanonicalizer extends AbstractConfiguredArtifactTypeUtil<ContentCanonicalizer> implements ContentCanonicalizer {

    public ConfiguredContentCanonicalizer(ArtifactTypeConfiguration artifactType) {
        super(artifactType, artifactType.getContentCanonicalizer());
    }

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return this.delegate.canonicalize(content, resolvedReferences);
    }

    @Override
    protected ContentCanonicalizer createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
        return new JavaClassContentCanonicalizerDelegate(artifactType, provider);
    }

    private class JavaClassContentCanonicalizerDelegate extends AbstractJavaClassDelegate implements ContentCanonicalizer {
        public JavaClassContentCanonicalizerDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            super(artifactType, provider);
        }

        @Override
        public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            return this.instance.canonicalize(content, resolvedReferences);
        }
    }

    @Override
    protected ContentCanonicalizer createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception {
        return new WebhookContentCanonicalizerDelegate(artifactType, provider);
    }

    private class WebhookContentCanonicalizerDelegate extends AbstractWebhookDelegate<ContentCanonicalizerRequest, ContentCanonicalizerResponse> implements ContentCanonicalizer {

        protected WebhookContentCanonicalizerDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            // Create the request payload object
            ContentCanonicalizerRequest requestBody = new ContentCanonicalizerRequest();
            requestBody.setContent(WebhookBeanUtil.toWebhookBean(content));
            requestBody.setResolvedReferences(WebhookBeanUtil.toWebhookBean(resolvedReferences));

            try {
                ContentCanonicalizerResponse responseBody = invokeHook(requestBody, ContentCanonicalizerResponse.class);
                return WebhookBeanUtil.fromWebhookBean(responseBody.getTypedContent());
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
                return content;
            }
        }
    }
}
