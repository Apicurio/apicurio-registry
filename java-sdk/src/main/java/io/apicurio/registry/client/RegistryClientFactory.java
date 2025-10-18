package io.apicurio.registry.client;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;

/**
 * Factory for creating instances of {@link RegistryClient}. This factory centralizes
 * the creation logic and provides a unified method for creating clients with different
 * authentication configurations using {@link RegistryClientOptions}.
 */
public final class RegistryClientFactory {

    private static final Vertx vertx = Vertx.vertx();

    /**
     * Creates a RegistryClient using the provided options.
     *
     * @param options the configuration options for the client
     * @return a new RegistryClient instance
     * @throws IllegalArgumentException if options are invalid
     */
    public static RegistryClient create(RegistryClientOptions options) {
        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(options, vertx);
        return new RegistryClient(adapter);
    }
}