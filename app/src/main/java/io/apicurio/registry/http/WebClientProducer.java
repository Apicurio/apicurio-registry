package io.apicurio.registry.http;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class WebClientProducer {

    @Inject
    Vertx vertx;

    @Produces
    public WebClient produceWebClient() {
        return WebClient.create(vertx);
    }

}
