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

package io.apicurio.registry.serde.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;


public class SimpleTopicIdStrategy<T> implements ArtifactReferenceResolverStrategy<T, Object> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record, io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<T> parsedSchema) {
        KafkaSerdeRecord<Object> kdata = (KafkaSerdeRecord<Object>) data;
        return ArtifactReference.builder()
                .groupId(null)
                .artifactId(kdata.metadata().getTopic())
                .build();
    }

    /**
     * @see io.apicurio.registry.serde.strategy.ArtifactResolverStrategy#loadSchema()
     */
    @Override
    public boolean loadSchema() {
        return false;
    }

}
