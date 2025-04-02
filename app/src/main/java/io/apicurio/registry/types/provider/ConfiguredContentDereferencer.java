package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.types.webhooks.beans.ContentDereferencerRequest;
import io.apicurio.registry.types.webhooks.beans.ContentDereferencerResponse;

import java.util.Map;

public class ConfiguredContentDereferencer extends AbstractConfiguredArtifactTypeUtil<ContentDereferencer> implements ContentDereferencer {

    public ConfiguredContentDereferencer(ArtifactTypeConfiguration artifactType) {
        super(artifactType, artifactType.getContentDereferencer());
    }

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return this.delegate.dereference(content, resolvedReferences);
    }

    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        return this.delegate.rewriteReferences(content, resolvedReferenceUrls);
    }

    @Override
    protected ContentDereferencer createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
        return new ConfiguredContentDereferencer.JavaClassContentDereferencerDelegate(artifactType, provider);
    }

    private class JavaClassContentDereferencerDelegate extends AbstractJavaClassDelegate implements ContentDereferencer {
        public JavaClassContentDereferencerDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            super(artifactType, provider);
        }

        @Override
        public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            return this.instance.dereference(content, resolvedReferences);
        }

        @Override
        public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
            return this.instance.rewriteReferences(content, resolvedReferenceUrls);
        }
    }

    @Override
    protected ContentDereferencer createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception {
        return new ConfiguredContentDereferencer.WebhookContentDereferencerDelegate(artifactType, provider);
    }

    private class WebhookContentDereferencerDelegate extends AbstractWebhookDelegate<ContentDereferencerRequest, ContentDereferencerResponse> implements ContentDereferencer {

        protected WebhookContentDereferencerDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            // Create the request payload object
            ContentDereferencerRequest requestBody = new ContentDereferencerRequest();
            requestBody.setFunction(ContentDereferencerRequest.Function.dereference);
            requestBody.setContent(WebhookBeanUtil.typedContentToWebhookBean(content));
            requestBody.setResolvedReferences(WebhookBeanUtil.resolvedReferenceListToWebhookBean(resolvedReferences));

            try {
                ContentDereferencerResponse responseBody = invokeHook(requestBody, ContentDereferencerResponse.class);
                return WebhookBeanUtil.typedContentFromWebhookBean(responseBody.getTypedContent());
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
                return content;
            }
        }

        @Override
        public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
            // Create the request payload object
            ContentDereferencerRequest requestBody = new ContentDereferencerRequest();
            requestBody.setFunction(ContentDereferencerRequest.Function.rewriteReferences);
            requestBody.setContent(WebhookBeanUtil.typedContentToWebhookBean(content));
            requestBody.setResolvedReferenceUrls(WebhookBeanUtil.resolvedReferenceUrlListToWebhookBean(resolvedReferenceUrls));

            try {
                ContentDereferencerResponse responseBody = invokeHook(requestBody, ContentDereferencerResponse.class);
                return WebhookBeanUtil.typedContentFromWebhookBean(responseBody.getTypedContent());
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
                return content;
            }
        }
    }
}
