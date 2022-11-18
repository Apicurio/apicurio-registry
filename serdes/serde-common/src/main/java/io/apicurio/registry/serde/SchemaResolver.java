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

package io.apicurio.registry.serde;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;

/**
 *
 * This interface is kept for compatibility, It's recommended to migrate custom implementations to adhere the new interface {@link io.apicurio.registry.resolver.SchemaResolver}
 *
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
@Deprecated
public interface SchemaResolver<SCHEMA, DATA> extends io.apicurio.registry.resolver.SchemaResolver<SCHEMA, DATA>, Closeable {

    /**
     * Configure, if supported.
     *
     * @param configs the configs
     * @param isKey are we handling key or value
     */
    @Deprecated
    default void configure(Map<String, ?> configs, boolean isKey, SchemaParser<SCHEMA> schemaMapper) {
    }

    @Override
    @Deprecated
    public void setClient(RegistryClient client);

    @Deprecated
    default void setArtifactResolverStrategy(ArtifactResolverStrategy<SCHEMA> artifactResolverStrategy) {
        setArtifactResolverStrategy((ArtifactReferenceResolverStrategy)artifactResolverStrategy);
    }

    /**
     * Used by Serializers to lookup the schema for a given kafka record.
     * @param topic
     * @param headers, can be null
     * @param data
     * @param parsedSchema, can be null
     * @return SchemaLookupResult
     */
    @Deprecated
    public SchemaLookupResult<SCHEMA> resolveSchema(String topic, Headers headers, DATA data, ParsedSchema<SCHEMA> parsedSchema);

    /**
     * Used by Deserializers to lookup the schema for a given kafka record.
     * The schema resolver may use different pieces of information from the {@link ArtifactReference} depending on the configuration of the schema resolver.
     * @param reference
     * @return SchemaLookupResult
     */
    @Deprecated
    public SchemaLookupResult<SCHEMA> resolveSchemaByArtifactReference(ArtifactReference reference);

    /**
     * Hard reset cache
     */
    @Override
    @Deprecated
    public void reset();

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#configure(java.util.Map, io.apicurio.registry.resolver.SchemaParser)
     */
    @Override
    default void configure(Map<String, ?> configs, io.apicurio.registry.resolver.SchemaParser<SCHEMA, DATA> schemaMapper) {
        configure(configs, true, new SchemaParser() {

            /**
             * @see io.apicurio.registry.serde.SchemaParser#artifactType()
             */
            @Override
            public String artifactType() {
                return schemaMapper.artifactType();
            }

            /**
             * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
             */
            @Override
            public Object parseSchema(byte[] rawSchema) {
                return schemaMapper.parseSchema(rawSchema, Collections.emptyMap());
            }

        });
    }



    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#resolveSchema(io.apicurio.registry.resolver.data.Record)
     */
    @Override
    default io.apicurio.registry.resolver.SchemaLookupResult<SCHEMA> resolveSchema(Record<DATA> data) {
        KafkaSerdeRecord<DATA> kdata = (KafkaSerdeRecord<DATA>) data;
        KafkaSerdeMetadata metadata = kdata.metadata();
        io.apicurio.registry.resolver.ParsedSchema<SCHEMA> ps = getSchemaParser().getSchemaFromData(data);
        ParsedSchema<SCHEMA> compatps = ps == null ? null : new ParsedSchemaImpl<SCHEMA>().setParsedSchema(ps.getParsedSchema()).setRawSchema(ps.getRawSchema());
        return resolveSchema(metadata.getTopic(), metadata.getHeaders(), kdata.payload(), compatps).toCompat();
    }



    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#resolveSchemaByArtifactReference(io.apicurio.registry.resolver.strategy.ArtifactReference)
     */
    @Override
    default io.apicurio.registry.resolver.SchemaLookupResult<SCHEMA> resolveSchemaByArtifactReference(
            io.apicurio.registry.resolver.strategy.ArtifactReference reference) {
        return resolveSchemaByArtifactReference(ArtifactReference.builder()
                    .contentId(reference.getContentId())
                    .globalId(reference.getGlobalId())
                    .groupId(reference.getGroupId())
                    .artifactId(reference.getArtifactId())
                    .version(reference.getVersion())
                    .build())
                .toCompat();
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    default void close() throws IOException {
    }

}
