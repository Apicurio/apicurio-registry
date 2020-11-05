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

package io.apicurio.registry.utils.serde.strategy;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * We first check client-side cache for matching schema,
 * if none matches, we check server-side,
 * create new if none matches (and cache it).
 *
 * @author Ales Justin
 */
public class CachedSchemaIdStrategy<T> extends AbstractCrudIdStrategy<T> {

    /* We use string for the key ... */
    private Map<String, Long> cache = new ConcurrentHashMap<>();

    @Override
    protected long initialLookup(RegistryRestClient client, String artifactId, ArtifactType artifactType, T schema) {
        InputStream stream = toStream(schema);
        String content = IoUtil.toString(stream);
        // TODO add an option to search by strict content
        return cache.computeIfAbsent(
            content,
            k -> client.getArtifactMetaDataByContent(artifactId, true, toStream(schema)).getGlobalId()
        );
    }

    @Override
    protected void afterCreateArtifact(T schema, ArtifactMetaData amd) {
        InputStream stream = toStream(schema);
        String content = IoUtil.toString(stream);
        cache.put(content, amd.getGlobalId());
    }
}
