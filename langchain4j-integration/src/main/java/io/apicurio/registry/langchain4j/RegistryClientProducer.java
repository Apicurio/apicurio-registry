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

import io.apicurio.registry.rest.client.RegistryClient;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * CDI producer for RegistryClient instances.
 * <p>
 * This producer creates a RegistryClient based on the configuration provided
 * via ApicurioRegistryConfig. The client is created as an application-scoped
 * singleton for efficiency.
 *
 * @author Carles Arnal
 */
@Singleton
public class RegistryClientProducer {

    @Inject
    ApicurioRegistryConfig config;

    /**
     * Produces a RegistryClient instance configured from application properties.
     *
     * @return a configured RegistryClient
     */
    @Produces
    @Singleton
    public RegistryClient registryClient() {
        return RegistryClientFactory.create(config);
    }
}
