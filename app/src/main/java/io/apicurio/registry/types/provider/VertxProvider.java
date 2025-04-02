package io.apicurio.registry.types.provider;

import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VertxProvider {

    public static Vertx INSTANCE;

    @Inject
    Vertx vertx;

    @PostConstruct
    public void init() {
        INSTANCE = vertx != null ? vertx : Vertx.vertx();
    }

    @PreDestroy
    public void shutdown() {
        if (vertx == null) {
            INSTANCE.close();
        }
    }

    public Vertx getVertx() {
        return vertx;
    }

}
