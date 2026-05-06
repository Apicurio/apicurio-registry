package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;

import java.net.URI;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

public final class Client {

    private static Client instance;

    public static synchronized Client getInstance() {
        if (instance == null) {
            var vertx = VertxInstance.getVertx();
            instance = new Client(vertx);
        }
        return instance;
    }

    // Resets the cached client so a new connection is established on next use.
    public static synchronized void reset() {
        instance = null;
    }

    private final Vertx vertx;

    private RegistryClient registryClient;

    private HttpClient httpClient;

    private Client(Vertx vertx) {
        this.vertx = vertx;
    }

    public RegistryClient getRegistryClient() {
        var currentContext = Config.getInstance().read();
        if (isBlank(currentContext.getCurrentContext())) {
            throw new CliException("No current context is set. " +
                    "Run `acr context set <context>` " +
                    "or `acr context add example http://localhost:8080`.", APPLICATION_ERROR_RETURN_CODE);
        } else {
            if (registryClient == null) {
                try {
                    var uri = new URI(currentContext.getContext().get(currentContext.getCurrentContext()).getRegistryUrl());
                    if (uri.getPath() == null) {
                        uri = uri.resolve("/apis/registry/v3");
                    }
                    registryClient = RegistryClientFactory.create(
                            RegistryClientOptions.create(uri.toString(), vertx)
                    );
                } catch (Exception ex) {
                    throw new CliException("Could not create Registry client: " + ex.getMessage(),
                            APPLICATION_ERROR_RETURN_CODE);
                }
            }
            return registryClient;
        }
    }

    public HttpClient getHttpClient() {
        if (httpClient == null) {
            try {
                httpClient = vertx.createHttpClient();
            } catch (Exception ex) {
                throw new CliException("Could not create HTTP client: " + ex.getMessage(),
                        APPLICATION_ERROR_RETURN_CODE);
            }
        }
        return httpClient;
    }
}
