/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

/**
 * @author Fabian Martinez
 */
public abstract class AbstractKafkaSerializer<T, U> extends AbstractKafkaSerDe<T, U> implements Serializer<U> {

    public AbstractKafkaSerializer() {
        super();
    }

    public AbstractKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaSerializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerializer(RegistryClient client, ArtifactResolverStrategy<T> artifactResolverStrategy, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
        getSchemaResolver().setArtifactResolverStrategy(artifactResolverStrategy);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(new BaseKafkaSerDeConfig(configs), isKey);
    }

    /**
     * This method is useful in serdes such as AVRO, where the schema can be extracted from the data of the kafka record.
     * The result of this method is passed to the SchemaResolver, which then can use this schema to resolve the exact
     * artifact version in Apicurio Registry or to create the artifact if configured to do so.
     *
     * @param data
     * @return the ParsedSchema, containing both the raw schema (bytes) and the parsed schema. Can be null.
     */
    protected ParsedSchema<T> getSchemaFromData(U data) {
        return null;
    }

    protected abstract void serializeData(ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    protected abstract void serializeData(Headers headers, ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    @Override
    public byte[] serialize(String topic, U data) {
        return serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {

            ParsedSchema<T> schemaFromData = new LazyLoadedParsedSchema<T>(() -> Optional.ofNullable(getSchemaFromData(data)));

            SchemaLookupResult<T> schema = getSchemaResolver().resolveSchema(topic, headers, data, schemaFromData);

            ParsedSchema<T> parsedSchema = new ParsedSchemaImpl<T>()
                    .setRawSchema(schema.getRawSchema())
                    .setParsedSchema(schema.getSchema());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (headersHandler != null && headers != null) {
                headersHandler.writeHeaders(headers, schema.toArtifactReference());
                serializeData(headers, parsedSchema, data, out);
            } else {
                out.write(MAGIC_BYTE);
                getIdHandler().writeId(schema.toArtifactReference(), out);
                serializeData(parsedSchema, data, out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
