/*
 * Copyright 2022 Red Hat
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
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;

/**
 * {@link ArtifactReferenceResolverStrategy} implementation that simply returns {@link Metadata#artifactReference()} from the given {@link Record}
 * @author Fabian Martinez
 */
public class DynamicArtifactReferenceResolverStrategy<SCHEMA, DATA> implements ArtifactReferenceResolverStrategy<SCHEMA, DATA> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record, io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public ArtifactReference artifactReference(Record<DATA> data, ParsedSchema<SCHEMA> parsedSchema) {
        ArtifactReference reference = data.metadata().artifactReference();
        if (reference == null) {
            throw new IllegalStateException("Wrong configuration. Missing metadata.artifactReference in Record, it's required by " + this.getClass().getName());
        }
        return reference;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#loadSchema()
     */
    @Override
    public boolean loadSchema() {
        return false;
    }

}
