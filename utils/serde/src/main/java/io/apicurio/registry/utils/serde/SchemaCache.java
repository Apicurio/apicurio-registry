/*
 * Copyright 2020 Red Hat
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

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.apicurio.registry.client.RegistryRestClient;

/**
 * @author Ales Justin
 */
public abstract class SchemaCache<T> {
    private final RegistryRestClient client;
    private final Map<Long, T> schemas = new ConcurrentHashMap<>();

    public SchemaCache(RegistryRestClient client) {
        this.client = Objects.requireNonNull(client);
    }

    protected abstract T toSchema(InputStream schemaData);

    public T getSchema(long id) {
        return schemas.computeIfAbsent(id, key -> {
            try {
                InputStream artifactResponse = client.getArtifactByGlobalId(key);
                return toSchema(artifactResponse);
            } catch (Exception e) {
                throw new IllegalStateException(
                        String.format(
                            "Error [%s] retrieving schema: %s",
                            e.getMessage(),
                            key
                        ),
                        e
                    );
            }
        });
    }

    public void clear() {
        schemas.clear();
    }
}
