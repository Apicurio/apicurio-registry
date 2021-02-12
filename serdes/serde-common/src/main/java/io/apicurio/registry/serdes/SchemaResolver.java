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

package io.apicurio.registry.serdes;

import java.util.Map;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serdes.strategy.ArtifactIdStrategy;

/**
 * @author Fabian Martinez
 */
public interface SchemaResolver<SCHEMA, DATA> {

    /**
     * Configure, if supported.
     *
     * @param configs the configs
     * @param isKey are we handling key or value
     */
    default void configure(Map<String, ?> configs, boolean isKey, SchemaMapper<SCHEMA, DATA> schemaMapper) {
    }

    public void setClient(RegistryClient client);

    public void setArtifactIdStrategy(ArtifactIdStrategy<SCHEMA> artifactIdStrategy);

    public SchemaLookupResult<SCHEMA> resolveSchema(String topic, Headers headers, DATA data);

    public SchemaLookupResult<SCHEMA> resolveSchemaByGlobalId(long globalId);

    public SchemaLookupResult<SCHEMA> resolveSchemaByArtifactId(String groupId, String artifactId, int version);

}
