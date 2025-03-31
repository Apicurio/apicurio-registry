package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.webhooks.beans.ContentAccepterRequest;
import io.apicurio.registry.types.webhooks.beans.ResolvedReference;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ConfiguredContentAccepter implements ContentAccepter {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredContentAccepter.class);

    private final ArtifactTypeConfiguration artifactType;
    private final Provider provider;

    public ConfiguredContentAccepter(ArtifactTypeConfiguration artifactType) {
        this.artifactType = artifactType;
        this.provider = artifactType.getContentAccepter();
    }

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            if (provider instanceof WebhookProvider) {
                return acceptsContentWebhook((WebhookProvider) provider, content, resolvedReferences);
            }
            // TODO implement Java and Script providers
            return false;
        } catch (Throwable e) {
            log.error("Failed to accept content for " + artifactType.getArtifactType(), e);
            return false;
        }
    }

    private boolean acceptsContentWebhook(WebhookProvider provider, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws Throwable {
        Vertx vertx = VertxProvider.vertx;

        // Create the request payload object
        ContentAccepterRequest car = new ContentAccepterRequest();
        car.setContent(content.getContent().content());
        car.setContentType(content.getContentType());
        if (resolvedReferences != null && !resolvedReferences.isEmpty()) {
            car.setResolvedReferences(new ArrayList<>(resolvedReferences.size()));
            for (Map.Entry<String, TypedContent> entry : resolvedReferences.entrySet()) {
                ResolvedReference ref = new ResolvedReference();
                ref.setName(entry.getKey());
                ref.setContent(entry.getValue().getContent().content());
                ref.setContentType(entry.getValue().getContentType());
                car.getResolvedReferences().add(ref);
            }
        }

        // Create a vert.x WebClient.
        WebClient webClient = WebClient.create(vertx);

        // POST the request to the webhook endpoint
        HttpRequest<Buffer> request = webClient.postAbs(provider.getUrl()).putHeader("Content-Type", "application/json")
                .followRedirects(true);
        Future<HttpResponse<Buffer>> future = request.sendJson(car);

        // Wait for the response (vert.x is async).
        try {
            HttpResponse<Buffer> httpResponse = future.toCompletionStage().toCompletableFuture().get();
            if (httpResponse.statusCode() == 200) {
                return "true".equals(httpResponse.bodyAsString());
            } else {
                throw new Exception("Webhook request failed (" + httpResponse.statusCode() + "): " + httpResponse.statusMessage());
            }
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
