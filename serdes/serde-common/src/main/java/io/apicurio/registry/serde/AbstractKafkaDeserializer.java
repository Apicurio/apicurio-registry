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

import java.nio.ByteBuffer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.BaseKafkaDeserializerConfig;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.serde.utils.Utils;

/**
 * @author Ales Justin
 * @author Fabian Martinez
 */
public abstract class AbstractKafkaDeserializer<T, U> extends AbstractKafkaSerDe<T, U> implements Deserializer<U> {

    protected FallbackArtifactProvider fallbackArtifactProvider;

    public AbstractKafkaDeserializer() {
        super();
    }

    public AbstractKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaDeserializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaDeserializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#configure(io.apicurio.registry.serde.config.BaseKafkaSerDeConfig, boolean)
     */
    @Override
    protected void configure(BaseKafkaSerDeConfig config, boolean isKey) {
        super.configure(config, isKey);

        BaseKafkaDeserializerConfig deserializerConfig = new BaseKafkaDeserializerConfig(config.originals());

        Object fallbackProvider = deserializerConfig.getFallbackArtifactProvider();
        Utils.instantiate(FallbackArtifactProvider.class, fallbackProvider, this::setFallbackArtifactProvider);
        fallbackArtifactProvider.configure(config.originals(), isKey);

        if (fallbackArtifactProvider instanceof DefaultFallbackArtifactProvider) {
            if (!((DefaultFallbackArtifactProvider) fallbackArtifactProvider).isConfigured()) {
                //it's not configured, just remove it so it's not executed
                fallbackArtifactProvider = null;
            }
        }

    }

    /**
     * @param fallbackArtifactProvider the fallbackArtifactProvider to set
     */
    public void setFallbackArtifactProvider(FallbackArtifactProvider fallbackArtifactProvider) {
        this.fallbackArtifactProvider = fallbackArtifactProvider;
    }

    protected abstract U readData(ParsedSchema<T> schema, ByteBuffer buffer, int start, int length);

    protected abstract U readData(Headers headers, ParsedSchema<T> schema, ByteBuffer buffer, int start, int length);

    @Override
    public U deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        ArtifactReference artifactReference = getIdHandler().readId(buffer);

        SchemaLookupResult<T> schema = resolve(topic, null, data, artifactReference);

        int length = buffer.limit() - 1 - getIdHandler().idSize();
        int start = buffer.position() + buffer.arrayOffset();

        ParsedSchema<T> parsedSchema = new ParsedSchemaImpl<T>()
                .setRawSchema(schema.getRawSchema())
                .setParsedSchema(schema.getSchema());

        return readData(parsedSchema, buffer, start, length);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        // check if data contains the magic byte
        if (data[0] == MAGIC_BYTE){
            return deserialize(topic, data);
        } else if (headers == null){
            throw new IllegalStateException("Headers cannot be null");
        } else {
            ArtifactReference artifactReference = headersHandler.readHeaders(headers);

            SchemaLookupResult<T> schema = resolve(topic, headers, data, artifactReference);

            ByteBuffer buffer = ByteBuffer.wrap(data);
            int length = buffer.limit();
            int start = buffer.position();

            ParsedSchema<T> parsedSchema = new ParsedSchemaImpl<T>()
                    .setRawSchema(schema.getRawSchema())
                    .setParsedSchema(schema.getSchema());

            return readData(headers, parsedSchema, buffer, start, length);
        }
    }

    private SchemaLookupResult<T> resolve(String topic, Headers headers, byte[] data, ArtifactReference artifactReference) {
        try {
            return getSchemaResolver().resolveSchemaByArtifactReference(artifactReference);
        } catch (RuntimeException e) {
            if (fallbackArtifactProvider == null) {
                throw e;
            } else {
                try {
                    ArtifactReference fallbackReference = fallbackArtifactProvider.get(topic, headers, data);
                    return getSchemaResolver().resolveSchemaByArtifactReference(fallbackReference);
                } catch (RuntimeException fe) {
                    fe.addSuppressed(e);
                    throw fe;
                }
            }
        }
    }

}
