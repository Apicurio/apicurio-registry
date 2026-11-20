package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.auth.CredentialStore;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

@ApplicationScoped
public class Client {

    @Inject
    Vertx vertx;

    @Inject
    Config config;

    @Inject
    CredentialStore credentialStore;

    // HTTP client hardening for the auto-update download flow.
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int IDLE_TIMEOUT_SECONDS = 120;
    private static final int MAX_REDIRECTS = 5;

    // Registry calls: without a read timeout, a request whose connection stalls (no bytes, no error)
    // waits forever. A long-running request (an import) gets a longer one.
    static final long REGISTRY_READ_IDLE_TIMEOUT_MILLIS = 120_000;
    static final long REGISTRY_LONG_REQUEST_READ_IDLE_TIMEOUT_MILLIS = 3_600_000;

    private RegistryClient registryClient;

    private HttpClient httpClient;

    /**
     * Set from {@code --verbose}, before the command runs. Makes the Registry client log the raw
     * request and response of every call it makes.
     */
    @Setter
    @Getter
    private boolean httpLoggingEnabled;

    public RegistryClient getRegistryClient() {
        requireCurrentContext();
        if (registryClient == null) {
            registryClient = createRegistryClient(httpLoggingEnabled, true);
        }
        return registryClient;
    }

    /**
     * A Registry client that never logs request or response bodies, even with {@code --verbose}.
     * For calls whose body carries secrets the SDK cannot recognise, such as headers forwarded to
     * another server. Always a new client: the cached one may have been created with logging on
     * by an earlier command in the same JVM.
     */
    public RegistryClient getRegistryClientWithoutHttpLogging() {
        return createRegistryClient(false, true);
    }

    /**
     * A Registry client for a request the server may take long to answer without sending anything,
     * such as an import: its read timeout is an hour rather than two minutes.
     */
    public RegistryClient getRegistryClientForLongRequests() {
        return createRegistryClient(httpLoggingEnabled, false);
    }

    private void requireCurrentContext() {
        if (isBlank(config.read().getCurrentContext())) {
            throw new CliException("No current context is set. " +
                    "Run `acr context use <context>` " +
                    "or `acr context create <name> <url>`.", APPLICATION_ERROR_RETURN_CODE);
        }
    }

    /**
     * The options of a Registry client, before authentication is configured.
     */
    static RegistryClientOptions registryClientOptions(final String url, final Vertx vertx, final boolean logHttp,
            final boolean shortReadTimeout) {
        final var options = RegistryClientOptions.create(url, vertx);
        options.requestTimeout(CONNECT_TIMEOUT_MILLIS,
                shortReadTimeout ? REGISTRY_READ_IDLE_TIMEOUT_MILLIS : REGISTRY_LONG_REQUEST_READ_IDLE_TIMEOUT_MILLIS);
        if (logHttp) {
            options.enableHttpLogging();
        }
        return options;
    }

    private RegistryClient createRegistryClient(final boolean logHttp, final boolean shortReadTimeout) {
        requireCurrentContext();
        var currentContext = config.read();
        try {
            var uri = new URI(currentContext.getContext().get(currentContext.getCurrentContext()).getRegistryUrl());
            if (uri.getPath() == null) {
                uri = uri.resolve("/apis/registry/v3");
            }
            final var options = registryClientOptions(uri.toString(), vertx, logHttp, shortReadTimeout);
            final var context = currentContext.getContext().get(currentContext.getCurrentContext());
            configureAuth(options, context, currentContext.getCurrentContext());
            return RegistryClientFactory.create(options);
        } catch (Exception ex) {
            throw new CliException("Could not create Registry client: " + ex.getMessage(),
                    APPLICATION_ERROR_RETURN_CODE);
        }
    }

    public HttpClient getHttpClient() {
        if (httpClient == null) {
            try {
                final var options = new HttpClientOptions()
                        .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                        .setIdleTimeout(IDLE_TIMEOUT_SECONDS)
                        .setIdleTimeoutUnit(TimeUnit.SECONDS)
                        .setMaxRedirects(MAX_REDIRECTS);
                httpClient = vertx.createHttpClient(options);
            } catch (Exception ex) {
                throw new CliException("Could not create HTTP client: " + ex.getMessage(),
                        APPLICATION_ERROR_RETURN_CODE);
            }
        }
        return httpClient;
    }

    private void configureAuth(final RegistryClientOptions options,
                               final ConfigModel.Context context,
                               final String contextName) {
        if (ConfigModel.AUTH_TYPE_BASIC.equals(context.getAuthType())) {
            final var password = requireCredential(contextName, ConfigModel.CREDENTIAL_KEY_PASSWORD,
                    context.isUnsafeCredentialStorage());
            options.basicAuth(context.getUsername(), password);
        } else if (ConfigModel.AUTH_TYPE_OAUTH2.equals(context.getAuthType())) {
            final var clientSecret = requireCredential(contextName, ConfigModel.CREDENTIAL_KEY_CLIENT_SECRET,
                    context.isUnsafeCredentialStorage());
            options.oauth2(context.getTokenEndpoint(), context.getClientId(), clientSecret, context.getScope());
        } else if (!isBlank(context.getAuthType())) {
            throw new CliException("Unsupported auth type '" + context.getAuthType()
                    + "'. Run 'acr login' to reconfigure authentication.", APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private String requireCredential(final String contextName, final String key,
                                     final boolean unsafeStorage) {
        final var value = credentialStore.retrieve(contextName, key, unsafeStorage);
        if (isBlank(value)) {
            throw new CliException("Credentials not found for context '" + contextName
                    + "'. Run 'acr login' to authenticate.", APPLICATION_ERROR_RETURN_CODE);
        }
        return value;
    }

    /**
     * Resets cached clients. Should be called between tests to avoid state leaking.
     */
    public void reset() {
        registryClient = null;
        httpClient = null;
        httpLoggingEnabled = false;
    }
}
