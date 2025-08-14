package io.apicurio.registry.resolver.client;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;

import java.util.logging.Logger;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;

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

        if (baseUrl.contains("/apis/v2/")) {
            return create_v2(config, ivertx, shouldCloseVertx);
        } else {
            return create_v3(config, ivertx, shouldCloseVertx);
        }
    }

    private static RegistryClientFacade create_v3(SchemaResolverConfig config, Vertx vertx, boolean shouldCloseVertx) {
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

        var client = RegistryClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl(client, shouldCloseVertx ? vertx : null);
    }

    private static RegistryClientFacade create_v2(SchemaResolverConfig config, Vertx vertx, boolean shouldCloseVertx) {
        logger.warning("Using a deprecated version (2.x) of Apicurio Registry.  It is recommended to upgrade your Apicurio Registry.");
        String baseUrl = config.getRegistryUrl();
        String tokenEndpoint = config.getTokenEndpoint();

        io.apicurio.registry.rest.client.v2.RegistryClient client;
        try {
            if (tokenEndpoint != null) {
                client = configureClientWithBearerAuthentication_v2(config, vertx, baseUrl, tokenEndpoint);
            } else {
                String username = config.getAuthUsername();

                if (username != null) {
                    client = configureClientWithBasicAuth_V2(config, vertx, baseUrl, username);
                } else {
                    var adapter = new VertXRequestAdapter(vertx);
                    adapter.setBaseUrl(baseUrl);
                    client = new io.apicurio.registry.rest.client.v2.RegistryClient(adapter);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return new RegistryClientFacadeImpl_v2(client, shouldCloseVertx ? vertx : null);
    }

    private static io.apicurio.registry.rest.client.v2.RegistryClient configureClientWithBearerAuthentication_v2(SchemaResolverConfig config, Vertx vertx,
                                                                                                                 String registryUrl, String tokenEndpoint) {
        RequestAdapter auth = configureAuthWithUrl(config, vertx, tokenEndpoint);
        auth.setBaseUrl(registryUrl);
        return new io.apicurio.registry.rest.client.v2.RegistryClient(auth);
    }

    private static RequestAdapter configureAuthWithUrl(SchemaResolverConfig config, Vertx vertx, String tokenEndpoint) {
        final String clientId = config.getAuthClientId();

        if (clientId == null) {
            throw new IllegalArgumentException(
                    "Missing registry auth clientId, set " + SchemaResolverConfig.AUTH_CLIENT_ID);
        }
        final String clientSecret = config.getAuthClientSecret();

        if (clientSecret == null) {
            throw new IllegalArgumentException(
                    "Missing registry auth secret, set " + SchemaResolverConfig.AUTH_CLIENT_SECRET);
        }

        final String clientScope = config.getAuthClientScope();

        return new VertXRequestAdapter(
                buildOIDCWebClient(vertx, tokenEndpoint, clientId, clientSecret, clientScope));
    }

    private static io.apicurio.registry.rest.client.v2.RegistryClient configureClientWithBasicAuth_V2(SchemaResolverConfig config, Vertx vertx, String registryUrl,
                                                                                                      String username) {

        final String password = config.getAuthPassword();

        if (password == null) {
            throw new IllegalArgumentException(
                    "Missing registry auth password, set " + SchemaResolverConfig.AUTH_PASSWORD);
        }

        var adapter = new VertXRequestAdapter(
                buildSimpleAuthWebClient(vertx, username, password));

        adapter.setBaseUrl(registryUrl);
        return new io.apicurio.registry.rest.client.v2.RegistryClient(adapter);
    }

}
