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

package io.apicurio.registry.utils.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.ParsedSchemaImpl;
import io.apicurio.registry.serde.SchemaLookupResult;
import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.SchemaResolverConfigurer;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.converter.json.FormatStrategy;
import io.apicurio.registry.utils.converter.json.PrettyFormatStrategy;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.storage.Converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Ales Justin
 * @author Fabian Martinez
 */
public class ExtJsonConverter extends SchemaResolverConfigurer<JsonNode, Object> implements Converter, SchemaParser<JsonNode>, AutoCloseable {
    private final JsonConverter jsonConverter;
    private final ObjectMapper mapper;
    private FormatStrategy formatStrategy;


    public ExtJsonConverter() {
        this(null);
    }

    public ExtJsonConverter(RegistryClient client) {
        super(client);
        this.jsonConverter = new JsonConverter();
        this.mapper = new ObjectMapper();
        this.formatStrategy = new PrettyFormatStrategy();
    }

    public ExtJsonConverter setFormatStrategy(FormatStrategy formatStrategy) {
        this.formatStrategy = Objects.requireNonNull(formatStrategy);
        return this;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey, this);
        Map<String, Object> wrapper = new HashMap<>(configs);
        wrapper.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, false);
        jsonConverter.configure(wrapper, isKey);
    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object value) {
        return fromConnectData(topic, null, schema, value);
    }

    @Override
    public byte[] fromConnectData(String topic, Headers headers, Schema schema, Object value) {
        JsonNode jsonSchema = jsonConverter.asJsonSchema(schema);
        String schemaString = jsonSchema.toString();
        ParsedSchema<JsonNode> parsedSchema = new ParsedSchemaImpl<JsonNode>()
                .setParsedSchema(jsonSchema)
                .setRawSchema(IoUtil.toBytes(schemaString));

        SchemaLookupResult<JsonNode> schemaLookupResult = getSchemaResolver().resolveSchema(topic, headers, value, parsedSchema);

        byte[] payload = jsonConverter.fromConnectData(topic, schema, value);

        return formatStrategy.fromConnectData(schemaLookupResult.getGlobalId(), payload);

    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {
        FormatStrategy.IdPayload ip = formatStrategy.toConnectData(value);

        long globalId = ip.getGlobalId();

        SchemaLookupResult<JsonNode> schemaLookupResult = getSchemaResolver().resolveSchemaByArtifactReference(ArtifactReference.builder().globalId(globalId).build());

        Schema schema = jsonConverter.asConnectSchema(schemaLookupResult.getSchema());

        byte[] payload = ip.getPayload();
        SchemaAndValue sav = jsonConverter.toConnectData(topic, payload);

        return new SchemaAndValue(schema, sav.value());
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public ArtifactType artifactType() {
        return ArtifactType.KCONNECT;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public JsonNode parseSchema(byte[] rawSchema) {
        try {
            return mapper.readTree(rawSchema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws Exception {
        jsonConverter.close();
    }

}
