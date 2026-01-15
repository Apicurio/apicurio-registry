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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration interface for Apicurio Registry LangChain4j integration.
 * <p>
 * Configuration can be provided via application.properties or environment variables:
 * <pre>
 * # application.properties
 * apicurio.registry.url=http://localhost:8080
 * apicurio.registry.default-group=default
 *
 * # Environment variables
 * APICURIO_REGISTRY_URL=http://localhost:8080
 * APICURIO_REGISTRY_DEFAULT_GROUP=default
 * </pre>
 *
 * @author Carles Arnal
 */
@ConfigMapping(prefix = "apicurio.registry")
public interface ApicurioRegistryConfig {

    /**
     * The base URL of the Apicurio Registry server.
     *
     * @return the registry URL
     */
    @WithDefault("http://localhost:8080")
    String url();

    /**
     * The default group ID to use when not specified.
     *
     * @return the default group ID
     */
    @WithDefault("default")
    String defaultGroup();

    /**
     * Optional authentication token for accessing the registry.
     *
     * @return the authentication token, if configured
     */
    Optional<String> authToken();

    /**
     * Whether to cache prompt templates in memory.
     *
     * @return true if caching is enabled
     */
    @WithDefault("true")
    boolean cacheEnabled();

    /**
     * OAuth2 client configuration for registry authentication.
     *
     * @return OAuth2 configuration
     */
    Optional<OAuth2Config> oauth2();

    /**
     * OAuth2 client configuration.
     */
    interface OAuth2Config {
        /**
         * The OAuth2 token endpoint URL.
         *
         * @return the token endpoint URL
         */
        String tokenEndpoint();

        /**
         * The OAuth2 client ID.
         *
         * @return the client ID
         */
        String clientId();

        /**
         * The OAuth2 client secret.
         *
         * @return the client secret
         */
        Optional<String> clientSecret();
    }
}
