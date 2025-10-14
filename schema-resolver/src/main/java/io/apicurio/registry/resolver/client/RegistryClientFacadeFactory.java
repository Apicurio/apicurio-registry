package io.apicurio.registry.resolver.client;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.vertx.core.Vertx;

import java.util.logging.Logger;

/**
 * Factory used to create instances of {@link RegistryClientFacade}.  Typically the
 * factory will create the correct implementation of the interface based on the
 * endpoint URL of the Registry Core API configured.  For example, if the URL contains
 * "/apis/v2", then a v2 implementation will be created.
 *
 */
public class RegistryClientFacadeFactory {

    private static final Logger logger = Logger.getLogger(RegistryClientFacadeFactory.class.getSimpleName());
    public static Vertx vertx;

    public static RegistryClientFacade create(SchemaResolverConfig config) {
        String baseUrl = config.getRegistryUrl();
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    "Missing registry base url, set " + SchemaResolverConfig.REGISTRY_URL);
        }

        Vertx ivertx = vertx == null ? Vertx.vertx() : vertx;
        boolean shouldCloseVertx = vertx == null;

        String endpointVersion = null;
        if (config.getRegistryUrlVersion() != null) {
            endpointVersion = config.getRegistryUrlVersion();
        }
        if (endpointVersion == null && baseUrl.contains("/apis/registry/v2")) {
            endpointVersion = "2";
        } else {
            endpointVersion = "3";
        }

        switch  (endpointVersion) {
            case "2":
                return create_v2(config, ivertx, shouldCloseVertx);
            case "3":
            default:
                return create_v3(config, ivertx, shouldCloseVertx);
        }
    }

    private static RegistryClientOptions buildClientOptions(SchemaResolverConfig config, Vertx vertx) {
        final String baseUrl = config.getRegistryUrl();
        final String tokenEndpoint = config.getTokenEndpoint();
        final String username = config.getAuthUsername();

        RegistryClientOptions clientOptions = RegistryClientOptions.create(baseUrl, vertx);
        try {
            if (tokenEndpoint != null) {
                final String clientId = config.getAuthClientId();
                final String clientSecret = config.getAuthClientSecret();
                final String clientScope = config.getAuthClientScope();

                if (clientId == null) {
                    throw new IllegalArgumentException(
                            "Missing registry auth clientId, set " + SchemaResolverConfig.AUTH_CLIENT_ID);
                }

                if (clientSecret == null) {
                    throw new IllegalArgumentException(
                            "Missing registry auth secret, set " + SchemaResolverConfig.AUTH_CLIENT_SECRET);
                }

                clientOptions.oauth2(tokenEndpoint, clientId, clientSecret, clientScope);
            } else if (username != null) {
                final String password = config.getAuthPassword();

                if (password == null) {
                    throw new IllegalArgumentException(
                            "Missing registry auth password, set " + SchemaResolverConfig.AUTH_PASSWORD);
                }

                clientOptions.basicAuth(username, password);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        // FIXME push retry options into the SchemaResolverConfig
        if (Boolean.TRUE) {
            clientOptions.retry();
        }

        return clientOptions;
    }

    private static RegistryClientFacade create_v3(SchemaResolverConfig config, Vertx vertx, boolean shouldCloseVertx) {
        RegistryClientOptions clientOptions = buildClientOptions(config, vertx);
        var client = RegistryClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl(client, shouldCloseVertx ? vertx : null);
    }

    private static RegistryClientFacade create_v2(SchemaResolverConfig config, Vertx vertx, boolean shouldCloseVertx) {
        logger.warning("Using a deprecated version (2.x) of Apicurio Registry.  It is recommended to upgrade your Apicurio Registry.");
        RegistryClientOptions clientOptions = buildClientOptions(config, vertx);
        var client = RegistryV2ClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl_v2(client, shouldCloseVertx ? vertx : null);
    }

}
