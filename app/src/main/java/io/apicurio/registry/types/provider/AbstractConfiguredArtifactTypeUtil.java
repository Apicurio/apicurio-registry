package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.config.artifactTypes.ScriptProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

public abstract class AbstractConfiguredArtifactTypeUtil<T> {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final T delegate;

    protected class AbstractWebhookDelegate<I, O> {

        protected final ArtifactTypeConfiguration artifactType;
        protected final WebhookProvider provider;

        protected AbstractWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            this.artifactType = artifactType;
            this.provider = provider;
        }

        protected O invokeHook(I requestBody, Class<O> outputClass) throws Throwable {
            Vertx vertx = VertxProvider.vertx;

            // Create a vert.x WebClient.
            WebClient webClient = WebClient.create(vertx);

            // POST the request to the webhook endpoint
            HttpRequest<Buffer> request = webClient.postAbs(provider.getUrl()).putHeader("Content-Type", "application/json")
                    .followRedirects(true);
            Future<HttpResponse<Buffer>> future = request.sendJson(requestBody);

            // Wait for the response (vert.x is async).
            try {
                HttpResponse<Buffer> httpResponse = future.toCompletionStage().toCompletableFuture().get();
                if (httpResponse.statusCode() == 200) {
                    return (O) getResponseBody(outputClass, httpResponse);
                } else {
                    throw new Exception("Webhook request failed (" + httpResponse.statusCode() + "): " + httpResponse.statusMessage());
                }
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }

        protected O getResponseBody(Class<O> outputClass, HttpResponse<Buffer> httpResponse) {
            return httpResponse.bodyAsJson(outputClass);
        }
    }

    protected class AbstractJavaClassDelegate {
        protected final ArtifactTypeConfiguration artifactType;
        protected final JavaClassProvider provider;
        protected final Class<T> javaClass;
        protected final T instance;

        public AbstractJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            this.artifactType = artifactType;
            this.provider = provider;
            this.javaClass = (Class<T>) loadJavaClassFromProvider(provider);
            this.instance = (T) instantiateJavaClass(javaClass);
        }

        private Class<?> loadJavaClassFromProvider(JavaClassProvider provider) throws Exception {
            try {
                String fqcn = provider.getClassname();
                return ClassUtils.getClass(fqcn);
            } catch (ClassNotFoundException e) {
                throw new Exception("JavaClass artifact type util failed (class not found): " + provider.getClassname());
            }
        }

        private T instantiateJavaClass(Class<?> javaClass) throws Exception {
            try {
                return (T) javaClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new Exception("JavaClass artifact type util failed (could not instantiate class): " + e.getMessage(), e);
            }
        }
    }

    public AbstractConfiguredArtifactTypeUtil(ArtifactTypeConfiguration artifactType, Provider provider) {
        try {
            this.delegate = createDelegate(artifactType, provider);
        } catch (Exception e) {
            throw new ArtifactTypeConfigurationException("Error detected configuring artifact type: " + artifactType.getArtifactType(), e);
        }
    }

    private T createDelegate(ArtifactTypeConfiguration artifactType, Provider provider) throws Exception {
        if (provider instanceof WebhookProvider) {
            return createWebhookDelegate(artifactType, (WebhookProvider) provider);
        } else if (provider instanceof JavaClassProvider) {
            return createJavaClassDelegate(artifactType, (JavaClassProvider) provider);
        } else if (provider instanceof ScriptProvider) {
            throw new Exception("ScriptProvider not yet implemented.");
        } else {
            throw new Exception("Unknown provider type: " + provider.getClass().getName());
        }
    }

    protected abstract T createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception;

    protected abstract T createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception;
}
