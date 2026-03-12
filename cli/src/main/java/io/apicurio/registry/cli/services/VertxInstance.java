package io.apicurio.registry.cli.services;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class VertxInstance {

    private static Vertx vertx;

    public static Vertx getVertx() {
        if (vertx == null) {
            var options = new VertxOptions();
            vertx = Vertx.vertx(options);
        }
        return vertx;
    }

    private VertxInstance() {
    }
}
