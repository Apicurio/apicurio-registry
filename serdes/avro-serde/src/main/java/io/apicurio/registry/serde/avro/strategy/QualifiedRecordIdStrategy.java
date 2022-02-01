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

package io.apicurio.registry.serde.avro.strategy;

import org.apache.avro.Schema;
import org.apache.kafka.common.errors.SerializationException;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.strategy.ArtifactReference;

/**
 * @author Jan Vyhnanek
 */
public class QualifiedRecordIdStrategy implements ArtifactReferenceResolverStrategy<Schema, Object> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record, io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public io.apicurio.registry.resolver.strategy.ArtifactReference artifactReference(Record<Object> data, ParsedSchema<Schema> parsedSchema) {
        if (parsedSchema != null && parsedSchema.getParsedSchema() != null && parsedSchema.getParsedSchema().getType() == Schema.Type.RECORD) {
            return ArtifactReference.builder()
                                    .groupId(null)
                                    .artifactId(parsedSchema.getParsedSchema().getFullName())
                                    .build();
        }
        throw new SerializationException("The message must only be an Avro record schema!");
    }

}
