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

    public static RegistryClientFacade create(SchemaResolverConfig config) {
        String baseUrl = config.getRegistryUrl();
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    "Missing registry base url, set " + SchemaResolverConfig.REGISTRY_URL);
        }

        Vertx vertx = config.getVertx();

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
                return create_v2(config, vertx);
            case "3":
            default:
                return create_v3(config, vertx);
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

        // Configure TLS/SSL
        String truststoreLocation = config.getTlsTruststoreLocation();
        String truststorePassword = config.getTlsTruststorePassword();
        String truststoreType = config.getTlsTruststoreType();
        String certificates = config.getTlsCertificates();
        boolean trustAll = config.getTlsTrustAll();
        boolean verifyHost = config.getTlsVerifyHost();

        if (trustAll) {
            clientOptions.trustAll(true);
        } else if (truststoreLocation != null) {
            if ("JKS".equalsIgnoreCase(truststoreType)) {
                clientOptions.trustStoreJks(truststoreLocation, truststorePassword);
            } else if ("PKCS12".equalsIgnoreCase(truststoreType) || "P12".equalsIgnoreCase(truststoreType)) {
                clientOptions.trustStorePkcs12(truststoreLocation, truststorePassword);
            } else if ("PEM".equalsIgnoreCase(truststoreType)) {
                clientOptions.trustStorePem(truststoreLocation);
            }
        } else if (certificates != null) {
            // Detect if certificates contains PEM content or file paths
            if (certificates.contains("-----BEGIN CERTIFICATE-----")) {
                // It's PEM certificate content - pass as string
                clientOptions.trustStorePemContent(certificates);
            } else {
                // It's a comma-separated list of PEM certificate file paths
                String[] certPaths = certificates.split(",");
                for (int i = 0; i < certPaths.length; i++) {
                    certPaths[i] = certPaths[i].trim();
                }
                clientOptions.trustStorePem(certPaths);
            }
        }

        if (!verifyHost) {
            clientOptions.verifyHost(false);
        }

        // Configure proxy
        String proxyHost = config.getProxyHost();
        Integer proxyPort = config.getProxyPort();

        if (proxyHost != null && proxyPort != null) {
            clientOptions.proxy(proxyHost, proxyPort);

            // Configure proxy authentication if credentials are provided
            String proxyUsername = config.getProxyUsername();
            String proxyPassword = config.getProxyPassword();

            if (proxyUsername != null && proxyPassword != null) {
                clientOptions.proxyAuth(proxyUsername, proxyPassword);
            }
        }

        return clientOptions;
    }

    private static RegistryClientFacade create_v3(SchemaResolverConfig config, Vertx vertx) {
        RegistryClientOptions clientOptions = buildClientOptions(config, vertx);
        var client = RegistryClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl(client);
    }

    private static RegistryClientFacade create_v2(SchemaResolverConfig config, Vertx vertx) {
        logger.warning("Using a deprecated version (2.x) of Apicurio Registry.  It is recommended to upgrade your Apicurio Registry to version 3.");
        RegistryClientOptions clientOptions = buildClientOptions(config, vertx);
        var client = RegistryV2ClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl_v2(client);
    }

}
