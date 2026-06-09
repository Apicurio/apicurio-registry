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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;

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

    private RegistryClient registryClient;

    private HttpClient httpClient;

    public RegistryClient getRegistryClient() {
        var currentContext = config.read();
        if (isBlank(currentContext.getCurrentContext())) {
            throw new CliException("No current context is set. " +
                    "Run `acr context use <context>` " +
                    "or `acr context create <name> <url>`.", APPLICATION_ERROR_RETURN_CODE);
        } else {
            if (registryClient == null) {
                try {
                    var uri = new URI(currentContext.getContext().get(currentContext.getCurrentContext()).getRegistryUrl());
                    if (uri.getPath() == null) {
                        uri = uri.resolve("/apis/registry/v3");
                    }
                    final var options = RegistryClientOptions.create(uri.toString(), vertx);
                    final var context = currentContext.getContext().get(currentContext.getCurrentContext());
                    configureAuth(options, context, currentContext.getCurrentContext());
                    registryClient = RegistryClientFactory.create(options);
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

    private void configureAuth(final RegistryClientOptions options,
                               final ConfigModel.Context context,
                               final String contextName) {
        if (ConfigModel.AUTH_TYPE_BASIC.equals(context.getAuthType())) {
            final var password = requireCredential(contextName, ConfigModel.CREDENTIAL_KEY_PASSWORD);
            options.basicAuth(context.getUsername(), password);
        } else if (ConfigModel.AUTH_TYPE_OAUTH2.equals(context.getAuthType())) {
            final var clientSecret = requireCredential(contextName, ConfigModel.CREDENTIAL_KEY_CLIENT_SECRET);
            options.oauth2(context.getTokenEndpoint(), context.getClientId(), clientSecret, context.getScope());
        } else if (!isBlank(context.getAuthType())) {
            throw new CliException("Unsupported auth type '" + context.getAuthType()
                    + "'. Run 'acr login' to reconfigure authentication.", APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private String requireCredential(final String contextName, final String key) {
        final var value = credentialStore.retrieve(contextName, key);
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
    }
}
