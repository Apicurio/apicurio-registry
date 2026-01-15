/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.langchain4j;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.client.common.RegistryClientRequestAdapterFactory;
import io.apicurio.registry.client.common.Version;
import io.apicurio.registry.rest.client.RegistryClient;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.ext.web.client.WebClient;

/**
 * Factory for creating RegistryClient instances.
 * <p>
 * This factory provides static methods for creating RegistryClient instances
 * with various authentication configurations. It builds on top of the existing
 * Apicurio Registry SDK infrastructure.
 *
 * @author Carles Arnal
 */
public final class RegistryClientFactory {

    private RegistryClientFactory() {
        // Utility class
    }

    /**
     * Creates a RegistryClient with anonymous authentication.
     *
     * @param baseUrl the base URL of the registry (e.g., "http://localhost:8080")
     * @return a configured RegistryClient
     */
    public static RegistryClient create(String baseUrl) {
        RegistryClientOptions options = RegistryClientOptions.create(baseUrl);
        return createFromOptions(options);
    }

    /**
     * Creates a RegistryClient with basic authentication.
     *
     * @param baseUrl  the base URL of the registry
     * @param username the username
     * @param password the password
     * @return a configured RegistryClient
     */
    public static RegistryClient createWithBasicAuth(String baseUrl, String username, String password) {
        RegistryClientOptions options = RegistryClientOptions.create(baseUrl)
                .basicAuth(username, password);
        return createFromOptions(options);
    }

    /**
     * Creates a RegistryClient with OAuth2 client credentials authentication.
     *
     * @param baseUrl       the base URL of the registry
     * @param tokenEndpoint the OAuth2 token endpoint
     * @param clientId      the client ID
     * @param clientSecret  the client secret
     * @return a configured RegistryClient
     */
    public static RegistryClient createWithOAuth2(String baseUrl, String tokenEndpoint,
                                                   String clientId, String clientSecret) {
        return createWithOAuth2(baseUrl, tokenEndpoint, clientId, clientSecret, null);
    }

    /**
     * Creates a RegistryClient with OAuth2 client credentials authentication.
     *
     * @param baseUrl       the base URL of the registry
     * @param tokenEndpoint the OAuth2 token endpoint
     * @param clientId      the client ID
     * @param clientSecret  the client secret
     * @param scope         the OAuth2 scope (optional)
     * @return a configured RegistryClient
     */
    public static RegistryClient createWithOAuth2(String baseUrl, String tokenEndpoint,
                                                   String clientId, String clientSecret, String scope) {
        RegistryClientOptions options = RegistryClientOptions.create(baseUrl)
                .oauth2(tokenEndpoint, clientId, clientSecret, scope);
        return createFromOptions(options);
    }

    /**
     * Creates a RegistryClient based on ApicurioRegistryConfig.
     *
     * @param config the configuration
     * @return a configured RegistryClient
     */
    public static RegistryClient create(ApicurioRegistryConfig config) {
        RegistryClientOptions options = RegistryClientOptions.create(config.url());

        if (config.oauth2().isPresent()) {
            ApicurioRegistryConfig.OAuth2Config oauth2 = config.oauth2().get();
            options.oauth2(oauth2.tokenEndpoint(), oauth2.clientId(), oauth2.clientSecret().orElse(null));
        }

        return createFromOptions(options);
    }

    /**
     * Creates a RegistryClient with fully customized options.
     *
     * @param options the client options
     * @return a configured RegistryClient
     */
    public static RegistryClient createFromOptions(RegistryClientOptions options) {
        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3);
        return new RegistryClient(adapter);
    }

    /**
     * Creates a RegistryClient with a custom Vert.x WebClient.
     *
     * @param baseUrl   the base URL of the registry API (including /apis/registry/v3)
     * @param webClient the Vert.x WebClient to use
     * @return a configured RegistryClient
     */
    public static RegistryClient create(String baseUrl, WebClient webClient) {
        VertXRequestAdapter adapter = new VertXRequestAdapter(webClient);
        adapter.setBaseUrl(normalizeUrl(baseUrl));
        return new RegistryClient(adapter);
    }

    /**
     * Creates a RegistryClient with a custom request adapter.
     *
     * @param adapter the request adapter to use
     * @return a configured RegistryClient
     */
    public static RegistryClient create(RequestAdapter adapter) {
        return new RegistryClient(adapter);
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        // Ensure URL ends with /apis/registry/v3
        if (!url.endsWith("/apis/registry/v3")) {
            if (url.endsWith("/")) {
                return url + "apis/registry/v3";
            } else {
                return url + "/apis/registry/v3";
            }
        }
        return url;
    }
}
