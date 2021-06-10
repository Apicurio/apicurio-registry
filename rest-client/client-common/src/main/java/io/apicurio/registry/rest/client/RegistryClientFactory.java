/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest.client;

import io.apicurio.registry.rest.client.impl.RegistryClientImpl;
import io.apicurio.registry.rest.client.spi.RestClientProvider;
import io.apicurio.registry.rest.client.spi.RestClientServiceLoader;
import io.apicurio.registry.auth.Auth;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class RegistryClientFactory {

    private static final AtomicReference<RestClientProvider> providerReference = new AtomicReference<>();
    private static final RestClientServiceLoader serviceLoader = new RestClientServiceLoader();

    public static RegistryClient create(RegistryHttpClient registryHttpClient) {
        return new RegistryClientImpl(registryHttpClient);
    }

    public static RegistryClient create(String basePath) {
        return create(basePath, Collections.emptyMap(), null);
    }

    public static RegistryClient create(String baseUrl, Map<String, Object> configs) {
        return create(baseUrl, configs, null);
    }

    public static RegistryClient create(String baseUrl, Map<String, Object> configs, Auth auth) {
        RestClientProvider p = providerReference.get();
        if (p == null) {
            providerReference.compareAndSet(null, resolveProviderInstance());
            p = providerReference.get();
        }

        return new RegistryClientImpl(p.create(baseUrl, configs, auth));
    }

    public static boolean setProvider(RestClientProvider provider) {
        return providerReference.compareAndSet(null, provider);
    }

    private static RestClientProvider resolveProviderInstance() {
        return serviceLoader.providers(true)
                .next();
    }
}
