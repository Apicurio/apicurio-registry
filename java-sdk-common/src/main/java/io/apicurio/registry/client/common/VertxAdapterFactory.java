package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.common.auth.VertXAuthFactory;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.lang.annotation.Annotation;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory class for creating Vert.x-based RequestAdapter instances.
 *
 * <p>This class is isolated from the main RegistryClientRequestAdapterFactory to ensure
 * that Vert.x classes are only loaded when the Vert.x adapter is explicitly selected.
 * This prevents NoClassDefFoundError when using the JDK adapter without Vert.x on the classpath.</p>
 */
public final class VertxAdapterFactory {

    private static final Logger log = Logger.getLogger(VertxAdapterFactory.class.getName());

    private VertxAdapterFactory() {
        // Prevent instantiation
    }

    /**
     * Creates a Vert.x-based RequestAdapter configured with authentication, SSL/TLS, and other settings
     * from the provided options.
     *
     * @param options the configuration options
     * @return a fully configured Vert.x RequestAdapter
     * @throws IllegalArgumentException if options are invalid or authentication type is unsupported
     */
    public static RequestAdapter createAdapter(RegistryClientOptions options) {
        Vertx vertxToUse = getVertx(options);
        WebClientOptions webClientOptions = buildWebClientOptions(options);

        switch (options.getAuthType()) {
            case ANONYMOUS:
                return createVertxAnonymous(vertxToUse, webClientOptions);
            case BASIC:
                return createVertxBasicAuth(options.getUsername(), options.getPassword(), vertxToUse, webClientOptions);
            case OAUTH2:
                return createVertxOAuth2(options.getTokenEndpoint(), options.getClientId(),
                        options.getClientSecret(), options.getScope(), vertxToUse, webClientOptions);
            case CUSTOM_WEBCLIENT:
                return createVertxCustomWebClient(options);
            default:
                throw new IllegalArgumentException("Unsupported authentication type: " + options.getAuthType());
        }
    }

    private static Vertx getVertxFromCDI(String CDIClassName, String InstanceClassName) {
        try {
            var CDIClass = Class.forName(CDIClassName);
            var instanceClass = Class.forName(InstanceClassName);
            var CDI = CDIClass.getMethod("current").invoke(null);
            var vertxInstance = CDIClass.getMethod("select", Class.class, Annotation[].class).invoke(CDI, Vertx.class, new Annotation[]{});
            return (Vertx) instanceClass.getMethod("get").invoke(vertxInstance);
        } catch (Throwable t) {
            log.log(Level.FINE, "Attempt to retrieve a Vertx instance from CDI failed: "
                    + t.getClass().getCanonicalName() + ": " + t.getMessage());
            return null;
        }
    }

    private static Vertx getVertx(RegistryClientOptions options) {
        Vertx vertx = options.getVertx();
        if (vertx != null) {
            return vertx;
        }

        vertx = getVertxFromCDI("jakarta.enterprise.inject.spi.CDI", "jakarta.enterprise.inject.Instance");
        if (vertx == null) {
            vertx = getVertxFromCDI("javax.enterprise.inject.spi.CDI", "javax.enterprise.inject.Instance");
        }
        if (vertx != null) {
            log.log(Level.FINE, "Successfully retrieved a Vertx instance from CDI.");
            return vertx;
        }

        return DefaultVertxInstance.get();
    }

    private static RequestAdapter createVertxAnonymous(Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = webClientOptions == null ? WebClient.create(vertx) : WebClient.create(vertx, webClientOptions);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createVertxBasicAuth(String username, String password, Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = VertXAuthFactory.buildSimpleAuthWebClient(vertx, webClientOptions, username, password);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createVertxOAuth2(String tokenEndpoint,
                                                     String clientId, String clientSecret, String scope, Vertx vertx, WebClientOptions webClientOptions) {
        WebClient webClient = VertXAuthFactory.buildOIDCWebClient(vertx, webClientOptions, tokenEndpoint, clientId, clientSecret, scope);
        return new VertXRequestAdapter(webClient);
    }

    private static RequestAdapter createVertxCustomWebClient(RegistryClientOptions options) {
        WebClient webClient = options.getWebClient();
        if (webClient == null) {
            throw new IllegalArgumentException("WebClient cannot be null");
        }
        return new VertXRequestAdapter(webClient);
    }

    private static WebClientOptions buildWebClientOptions(RegistryClientOptions options) {
        boolean hasSslConfig = options.getTrustStoreType() != RegistryClientOptions.TrustStoreType.NONE
                || options.getKeyStoreType() != RegistryClientOptions.KeyStoreType.NONE
                || options.isTrustAll()
                || !options.isVerifyHost();

        boolean hasProxyConfig = options.getProxyHost() != null;

        if (!hasSslConfig && !hasProxyConfig) {
            return null;
        }

        WebClientOptions webClientOptions = new WebClientOptions();

        if (hasSslConfig) {
            webClientOptions.setSsl(true);
        }

        if (options.isTrustAll()) {
            webClientOptions.setTrustAll(true);
        }

        webClientOptions.setVerifyHost(options.isVerifyHost());

        switch (options.getTrustStoreType()) {
            case JKS:
                JksOptions jksOptions = new JksOptions()
                        .setPath(options.getTrustStorePath())
                        .setPassword(options.getTrustStorePassword());
                webClientOptions.setTrustOptions(jksOptions);
                break;
            case PKCS12:
                PfxOptions pfxOptions = new PfxOptions()
                        .setPath(options.getTrustStorePath())
                        .setPassword(options.getTrustStorePassword());
                webClientOptions.setTrustOptions(pfxOptions);
                break;
            case PEM:
                PemTrustOptions pemOptions = new PemTrustOptions();
                if (options.getPemCertContent() != null) {
                    Buffer certBuffer = Buffer.buffer(options.getPemCertContent());
                    pemOptions.addCertValue(certBuffer);
                } else if (options.getPemCertPaths() != null) {
                    for (String certPath : options.getPemCertPaths()) {
                        pemOptions.addCertPath(certPath);
                    }
                }
                webClientOptions.setTrustOptions(pemOptions);
                break;
            case NONE:
                break;
        }

        switch (options.getKeyStoreType()) {
            case JKS:
                JksOptions jksKeyStoreOptions = new JksOptions()
                        .setPath(options.getKeyStorePath())
                        .setPassword(options.getKeyStorePassword());
                webClientOptions.setKeyCertOptions(jksKeyStoreOptions);
                break;
            case PKCS12:
                PfxOptions pfxKeyStoreOptions = new PfxOptions()
                        .setPath(options.getKeyStorePath())
                        .setPassword(options.getKeyStorePassword());
                webClientOptions.setKeyCertOptions(pfxKeyStoreOptions);
                break;
            case PEM:
                PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions();
                if (options.getPemClientCertContent() != null && options.getPemClientKeyContent() != null) {
                    Buffer certBuffer = Buffer.buffer(options.getPemClientCertContent());
                    Buffer keyBuffer = Buffer.buffer(options.getPemClientKeyContent());
                    pemKeyCertOptions.addCertValue(certBuffer);
                    pemKeyCertOptions.addKeyValue(keyBuffer);
                } else if (options.getPemClientCertPath() != null && options.getPemClientKeyPath() != null) {
                    pemKeyCertOptions.addCertPath(options.getPemClientCertPath());
                    pemKeyCertOptions.addKeyPath(options.getPemClientKeyPath());
                }
                webClientOptions.setKeyCertOptions(pemKeyCertOptions);
                break;
            case NONE:
                break;
        }

        if (hasProxyConfig) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setHost(options.getProxyHost())
                    .setPort(options.getProxyPort());

            if (options.getProxyUsername() != null && !options.getProxyUsername().isEmpty()) {
                proxyOptions.setUsername(options.getProxyUsername());
                if (options.getProxyPassword() != null) {
                    proxyOptions.setPassword(options.getProxyPassword());
                }
            }

            webClientOptions.setProxyOptions(proxyOptions);
        }

        return webClientOptions;
    }
}
