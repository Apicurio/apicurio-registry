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

import io.apicurio.registry.client.RegistryService;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public abstract class SchemaCache<T> {
    private final RegistryService client;
    private final Map<Long, T> schemas = new ConcurrentHashMap<>();

    public SchemaCache(RegistryService client) {
        this.client = Objects.requireNonNull(client);
    }

    protected abstract T toSchema(Response response);

    public T getSchema(long id) {
        return schemas.computeIfAbsent(id, key -> {
            Response artifactResponse = client.getArtifactByGlobalId(key);
            Response.StatusType statusInfo = artifactResponse.getStatusInfo();
            if (statusInfo.getStatusCode() != 200) {
                throw new IllegalStateException(
                    String.format(
                        "Error [%s] retrieving schema: %s",
                        statusInfo.getReasonPhrase(),
                        key
                    )
                );
            }
            return toSchema(artifactResponse);
        });
    }

    public void clear() {
        schemas.clear();
    }
}
