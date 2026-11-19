package io.apicurio.registry.http;

import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.config.ConfigPropertyCategory;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class WebClientProducer {

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "apicurio.artifact-types.webhooks.connect-timeout.seconds", defaultValue = "5")
    @Info(category = ConfigPropertyCategory.CATEGORY_TYPES, description = "Timeout, in seconds, for connecting to an artifact type webhook.", availableSince = "3.4.0")
    int webhookConnectTimeoutSeconds;

    @ConfigProperty(name = "apicurio.artifact-types.webhooks.read-timeout.seconds", defaultValue = "10")
    @Info(category = ConfigPropertyCategory.CATEGORY_TYPES, description = "Timeout, in seconds, for an artifact type webhook to send data on an open connection.", availableSince = "3.4.0")
    int webhookReadTimeoutSeconds;

    @Produces
    @Default
    public WebClient produceWebClient() {
        return WebClient.create(vertx);
    }

    @Produces
    @WebhookClient
    public WebClient produceWebhooksWebClient() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(webhookConnectTimeoutSeconds))
                .setIdleTimeout(webhookReadTimeoutSeconds).setIdleTimeoutUnit(TimeUnit.SECONDS);
        return WebClient.create(vertx, options);
    }

}
