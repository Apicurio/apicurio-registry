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
import java.util.HashMap;
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
import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.SchemaResolver;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import io.apicurio.registry.serde.utils.Utils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Fabian Martinez
 */
public class JsonSchemaKafkaDeserializer<T> extends AbstractKafkaDeserializer<SchemaValidator, T> implements Deserializer<T>, SchemaParser<SchemaValidator> {

    protected static MedeiaJacksonApi api = new MedeiaJacksonApi();
    protected static ObjectMapper mapper = new ObjectMapper();

    private Boolean validationEnabled;
    /**
     * Optional, the full class name of the java class to deserialize
     */
    private Class<T> specificReturnClass;
    private MessageTypeSerdeHeaders serdeHeaders;

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
        JsonSchemaKafkaDeserializerConfig config = new JsonSchemaKafkaDeserializerConfig(configs, isKey);
        super.configure(config, isKey);

        if (validationEnabled == null) {
            this.validationEnabled = config.validationEnabled();
        }

        this.specificReturnClass = (Class<T>) config.getSpecificReturnClass();

        this.serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);

    }

    public boolean isValidationEnabled() {
        return validationEnabled != null && validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<SchemaValidator> schemaParser() {
        return this;
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
        return internalReadData(null, schema, buffer, start, length);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaDeserializer#readData(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.ParsedSchema, java.nio.ByteBuffer, int, int)
     */
    @Override
    protected T readData(Headers headers, ParsedSchema<SchemaValidator> schema, ByteBuffer buffer, int start, int length) {
        return internalReadData(headers, schema, buffer, start, length);
    }

    private T internalReadData(Headers headers, ParsedSchema<SchemaValidator> schema, ByteBuffer buffer, int start, int length) {
        byte[] data = new byte[length];
        System.arraycopy(buffer.array(), start, data, 0, length);

        try {
            JsonParser parser = mapper.getFactory().createParser(data);

            if (isValidationEnabled()) {
                parser = api.decorateJsonParser(schema.getParsedSchema(), parser);
            }

            Class<T> messageType = null;

            if (this.specificReturnClass != null) {
                messageType = this.specificReturnClass;
            } else if (headers == null) {
                JsonNode jsonSchema = mapper.readTree(schema.getRawSchema());

                String javaType = null;
                JsonNode javaTypeNode = jsonSchema.get("javaType");
                if (javaTypeNode != null && !javaTypeNode.isNull()) {
                    javaType = javaTypeNode.textValue();
                }
                //TODO if javaType is null, maybe warn something like this?
                //You can try configure the property \"apicurio.registry.serde.json-schema.java-type\" with the full class name to use for deserialization
                messageType = javaType == null ? null : Utils.loadClass(javaType);
            } else {
                String javaType = serdeHeaders.getMessageType(headers);
                messageType = javaType == null ? null : Utils.loadClass(javaType);
            }

            if (messageType == null) {
                //TODO maybe warn there is no message type and the deserializer will return a JsonNode
                return mapper.readTree(parser);
            } else {
                return mapper.readValue(parser, messageType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
