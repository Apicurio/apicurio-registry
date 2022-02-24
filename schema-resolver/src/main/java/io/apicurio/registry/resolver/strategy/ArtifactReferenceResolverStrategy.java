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

package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;

/**
 * This interface is used by the SchemaResolver to determine
 * the {@link ArtifactReference} under which the message schemas are located or should be registered
 * in the registry.
 *
 * @author Fabian Martinez
 */
public interface ArtifactReferenceResolverStrategy<SCHEMA, DATA> {

    /**
     * For a given Record, returns the {@link ArtifactReference} under which the message schemas are located or should be registered
     * in the registry.
     * @param data record for which we want to resolve the ArtifactReference
     * @param parsedSchema the schema of the record being resolved, can be null if {@link ArtifactReferenceResolverStrategy#loadSchema()} is set to false
     * @return the {@link ArtifactReference} under which the message schemas are located or should be registered
     */
    ArtifactReference artifactReference(Record<DATA> data, ParsedSchema<SCHEMA> parsedSchema);

    /**
     * Whether or not to load and pass the parsed schema to the {@link ArtifactReferenceResolverStrategy#artifactReference(Record, ParsedSchema)} lookup method
     */
    default boolean loadSchema() {
        return true;
    }

}
