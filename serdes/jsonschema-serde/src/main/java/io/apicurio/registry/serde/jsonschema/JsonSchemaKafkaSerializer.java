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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of the Kafka Serializer for JSON Schema use-cases. This serializer assumes that the
 * user's application needs to serialize a Java Bean to JSON data using Jackson. In addition to standard
 * serialization of the bean, this implementation can also optionally validate it against a JSON schema.
 *
 * @author eric.wittmann@gmail.com
 * @author Ales Justin
 * @author Fabian Martinez
 * @author Carles Arnal
 */
public class JsonSchemaKafkaSerializer<T> extends AbstractKafkaSerializer<JsonSchema, T> implements Serializer<T> {

    private ObjectMapper mapper;
    private final JsonSchemaParser<T> parser = new JsonSchemaParser<>();

    private Boolean validationEnabled;
    private MessageTypeSerdeHeaders serdeHeaders;

    public JsonSchemaKafkaSerializer() {
        super();
    }

    public JsonSchemaKafkaSerializer(RegistryClient client,
                                     ArtifactReferenceResolverStrategy<JsonSchema, T> artifactResolverStrategy,
                                     SchemaResolver<JsonSchema, T> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    public JsonSchemaKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public JsonSchemaKafkaSerializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(schemaResolver);
    }

    public JsonSchemaKafkaSerializer(RegistryClient client, Boolean validationEnabled) {
        this(client);
        this.validationEnabled = validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        JsonSchemaKafkaSerializerConfig config = new JsonSchemaKafkaSerializerConfig(configs);
        super.configure(config, isKey);

        if (validationEnabled == null) {
            this.validationEnabled = config.validationEnabled();
        }

        serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);

        if (null == mapper) {
            this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }
    }

    public boolean isValidationEnabled() {
        return validationEnabled != null && validationEnabled;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    /**
     * @param validationEnabled the validationEnabled to set
     */
    public void setValidationEnabled(Boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<JsonSchema, T> schemaParser() {
        return parser;
    }


    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.serde.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(ParsedSchema<JsonSchema> schema, T data, OutputStream out) throws IOException {
        //TODO add property to specify a jsonschema to allow for auto-register json schemas
        serializeData(null, schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers, io.apicurio.registry.serde.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<JsonSchema> schema, T data, OutputStream out) throws IOException {
        final byte[] dataBytes = mapper.writeValueAsBytes(data);
        if (isValidationEnabled()) {
            JsonSchemaValidationUtil.validateDataWithSchema(schema, dataBytes, mapper);
        }
        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
        }
        out.write(dataBytes);
    }
}
