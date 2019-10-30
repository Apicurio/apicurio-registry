/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.utils.serde;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;

import java.util.Map;

/**
 * Common class for both serializer and deserializer.
 *
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerDe {
    public static final String REGISTRY_URL_CONFIG_PARAM = "apicurio.registry.url";
    public static final String REGISTRY_CACHED_CONFIG_PARAM = "apicurio.registry.cached";

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int idSize = 8;

    private RegistryService client;

    public AbstractKafkaSerDe() {
    }

    public AbstractKafkaSerDe(RegistryService client) {
        this.client = client;
    }

    protected void configure(Map<String, ?> configs) {
        if (client == null) {
            String baseUrl = (String) configs.get(REGISTRY_URL_CONFIG_PARAM);
            if (baseUrl == null) {
                throw new IllegalArgumentException("Missing registry base url, set " + REGISTRY_URL_CONFIG_PARAM);
            }
            try {
                Object cached = configs.get(REGISTRY_CACHED_CONFIG_PARAM);
                if (isTrue(cached)) {
                    client = RegistryClient.cached(baseUrl);
                } else {
                    client = RegistryClient.create(baseUrl);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    protected static boolean isTrue(Object cached) {
        if (cached == null) {
            return false;
        }
        if (cached instanceof Boolean) {
            return (Boolean) cached;
        }
        if (cached instanceof String) {
            return Boolean.parseBoolean((String) cached);
        }
        return false;
    }

    protected RegistryService getClient() {
        return client;
    }

    public void reset() {
        if (client != null) {
            client.reset();
        }
    }

    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
