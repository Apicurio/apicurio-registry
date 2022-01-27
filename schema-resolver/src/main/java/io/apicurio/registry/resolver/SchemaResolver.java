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

package io.apicurio.registry.resolver;

import java.io.Closeable;
import java.util.Map;

import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;

/**
 * @author Fabian Martinez
 */
public interface SchemaResolver<SCHEMA, DATA> extends Closeable {

    /**
     * Configure, if supported.
     *
     * @param configs the configs
     * @param isKey are we handling key or value
     */
    default void configure(Map<String, ?> configs, SchemaParser<SCHEMA, DATA> schemaMapper) {
    }

    public void setClient(RegistryClient client);

    public void setArtifactResolverStrategy(ArtifactReferenceResolverStrategy<SCHEMA, DATA> artifactResolverStrategy);

    public SchemaParser<SCHEMA, DATA> getSchemaParser();

    /**
     * Used to register or to lookup a schema in the registry
     * @param data, record containing metadata about it that can be used by the resolver to lookup a schema in the registry
     * @return SchemaLookupResult
     */
    public SchemaLookupResult<SCHEMA> resolveSchema(Record<DATA> data);

    /**
     * The schema resolver may use different pieces of information from the {@link ArtifactReferenceImpl} depending on the configuration of the schema resolver.
     * @param reference
     * @return SchemaLookupResult
     */
    public SchemaLookupResult<SCHEMA> resolveSchemaByArtifactReference(ArtifactReference reference);

    /**
     * Hard reset cache
     */
    public void reset();

}
