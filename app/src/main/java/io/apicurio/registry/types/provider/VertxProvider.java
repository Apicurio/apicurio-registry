package io.apicurio.registry.types.provider;

import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VertxProvider {

    public static Vertx vertx;

    @PostConstruct
    public void init() {
        vertx = Vertx.vertx();
    }

    @PreDestroy
    public void shutdown() {
        vertx.close();
    }

    public Vertx getVertx() {
        return vertx;
    }

}
