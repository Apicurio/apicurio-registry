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

package io.apicurio.registry.serde.jsonschema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.StreamSchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.SchemaResolver;
import io.apicurio.registry.serde.SerdeConfigKeys;
import io.apicurio.registry.serde.utils.HeaderUtils;
import io.apicurio.registry.serde.utils.Utils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Fabian Martinez
 */
public class JsonSchemaKafkaDeserializer<T> extends AbstractKafkaDeserializer<SchemaValidator, T> implements Deserializer<T> {

    protected static MedeiaJacksonApi api = new MedeiaJacksonApi();
    protected static ObjectMapper mapper = new ObjectMapper();

    private Boolean validationEnabled;

    public JsonSchemaKafkaDeserializer() {
        super();
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client,
            SchemaResolver<SchemaValidator, T> schemaResolver) {
        super(client, schemaResolver);
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public JsonSchemaKafkaDeserializer(SchemaResolver<SchemaValidator, T> schemaResolver) {
        super(schemaResolver);
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client, Boolean validationEnabled) {
        this(client);
        this.validationEnabled = validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#configure(java.util.Map, boolean)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        if (validationEnabled == null) {
            Object ve = configs.get(SerdeConfigKeys.VALIDATION_ENABLED);
            this.validationEnabled = Utils.isTrue(ve);
        }

        //headers funcionality is always enabled for jsonschema
        headerUtils = new HeaderUtils((Map<String, Object>) configs, isKey);

        // TODO allow the schema to be configured here
    }

    public boolean isValidationEnabled() {
        return validationEnabled != null && validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public ArtifactType artifactType() {
        return ArtifactType.JSON;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public SchemaValidator parseSchema(byte[] rawSchema) {
        return api.loadSchema(new StreamSchemaSource(IoUtil.toStream(rawSchema)));
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected T readData(ParsedSchema<SchemaValidator> schema, ByteBuffer buffer, int start, int length) {
        try {
            JsonNode jsonSchema = mapper.readTree(schema.getRawSchema());

            JsonNode javaType = jsonSchema.get("javaType");
            if (javaType == null || javaType.isNull()) {
                throw new IllegalStateException("Missing javaType info in jsonschema, unable to deserialize.");
            }

            return internalReadData(getMessageType(javaType.textValue()), schema.getParsedSchema(), buffer, start, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected T readData(Headers headers, ParsedSchema<SchemaValidator> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(getMessageType(headers), schema.getParsedSchema(), buffer, start, length);
    }

    private T internalReadData(Class<T> messageType, SchemaValidator schema, ByteBuffer buffer, int start, int length) {
        byte[] data = new byte[length];
        System.arraycopy(buffer.array(), start, data, 0, length);

        try {
            JsonParser parser = mapper.getFactory().createParser(data);

            if (isValidationEnabled()) {
                parser = api.decorateJsonParser(schema, parser);
            }

            return mapper.readValue(parser, messageType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Gets the message type from the headers.  Throws if not found.
     *
     * @param headers the headers
     */
    protected Class<T> getMessageType(Headers headers) {
        String msgTypeName = headerUtils.getMessageType(headers);
        return getMessageType(msgTypeName);
    }

    @SuppressWarnings("unchecked")
    protected Class<T> getMessageType(String javaType) {
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(javaType);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            return (Class<T>) Class.forName(javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
