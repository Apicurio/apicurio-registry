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

package io.apicurio.registry.utils.serde;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.strategy.FindLatestIdStrategy;

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 */
public class JsonSchemaKafkaSerializer<T> extends JsonSchemaKafkaSerDe<JsonSchemaKafkaSerializer<T>> implements Serializer<T> {

    /**
     * Constructor.
     */
    public JsonSchemaKafkaSerializer() {
        this(null, null);
    }

    /**
     * Constructor.
     *
     * @param client            the client
     * @param validationEnabled the validation enabled flag
     */
    public JsonSchemaKafkaSerializer(RegistryRestClient client, Boolean validationEnabled) {
        super(client, validationEnabled);
        setGlobalIdStrategy(new FindLatestIdStrategy<>()); // the default is get latest
    }

    /**
     * @see org.apache.kafka.common.serialization.Serializer#serialize(java.lang.String, java.lang.Object)
     */
    @Override
    public byte[] serialize(String topic, T data) {
        // Headers are required when sending data using this serde impl
        throw new UnsupportedOperationException();
    }

    /**
     * @see org.apache.kafka.common.serialization.Serializer#serialize(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object)
     */
    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (data == null) {
            return null;
        }

        // Now serialize the data
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator generator = mapper.getFactory().createGenerator(baos);
            if (isValidationEnabled()) {
                String artifactId = getArtifactId(topic, data);
                long globalId = getGlobalId(artifactId, topic, data);
                headerUtils.addSchemaHeaders(headers, artifactId, globalId);

                SchemaValidator schemaValidator = getSchemaCache().getSchema(globalId);
                generator = api.decorateJsonGenerator(schemaValidator, generator);
            }
            headerUtils.addMessageTypeHeader(headers, data.getClass().getName());

            mapper.writeValue(generator, data);

            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Figure out the artifact ID from the topic name and data.
     *
     * @param topic the Kafka topic
     * @param data msg data
     */
    protected String getArtifactId(String topic, T data) {
        // Note - for JSON Schema, we don't yet have the schema so we pass null to the strategy.
        return getArtifactIdStrategy().artifactId(topic, isKey(), null);
    }

    /**
     * Gets the global id of the schema to use for validation.
     *
     * @param artifactId artifact id
     * @param topic      the topic
     * @param data       the msg data
     */
    protected long getGlobalId(String artifactId, String topic, T data) {
        // Note - for JSON Schema, we don't yet have the schema so we pass null to the strategy.
        return getGlobalIdStrategy().findId(getClient(), artifactId, ArtifactType.JSON, null);
    }

}
