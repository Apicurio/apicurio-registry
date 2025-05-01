package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.ScriptProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.script.ScriptingService;
import io.apicurio.registry.types.webhooks.beans.ContentAccepterRequest;

import java.util.Map;

public class ConfiguredContentAccepter extends AbstractConfiguredArtifactTypeUtil<ContentAccepter> implements ContentAccepter {

    public ConfiguredContentAccepter(HttpClientService httpClientService, ScriptingService scriptingService, ArtifactTypeConfiguration artifactType) {
        super(httpClientService, scriptingService, artifactType, artifactType.getContentAccepter());
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
            ContentAccepterRequest requestBody = createRequest(content, resolvedReferences);

            try {
                return invokeHook(requestBody, Boolean.class);
            } catch (Throwable e) {
                log.error("Error invoking webhook", e);
                return false;
            }
        }

    }

    @Override
    protected ContentAccepter createScriptDelegate(ArtifactTypeConfiguration artifactType, ScriptProvider provider) throws Exception {
        return new ScriptContentAccepterDelegate(artifactType, provider);
    }

    private class ScriptContentAccepterDelegate extends AbstractScriptDelegate<ContentAccepterRequest, Boolean> implements ContentAccepter {

        protected ScriptContentAccepterDelegate(ArtifactTypeConfiguration artifactType, ScriptProvider provider) {
            super(artifactType, provider);
        }

        @Override
        public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
            ContentAccepterRequest requestBody = createRequest(content, resolvedReferences);

            try {
                return executeScript(requestBody, Boolean.class);
            } catch (Throwable e) {
                log.error("Error executing script", e);
                return false;
            }
        }

    }

    private static ContentAccepterRequest createRequest(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        ContentAccepterRequest requestBody = new ContentAccepterRequest();
        requestBody.setTypedContent(WebhookBeanUtil.typedContentToWebhookBean(content));
        requestBody.setResolvedReferences(WebhookBeanUtil.resolvedReferenceListToWebhookBean(resolvedReferences));
        return requestBody;
    }
}
