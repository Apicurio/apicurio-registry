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

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.converter.json.FormatStrategy;
import io.apicurio.registry.utils.converter.json.PrettyFormatStrategy;
import io.apicurio.registry.utils.serde.AbstractKafkaStrategyAwareSerDe;
import io.apicurio.registry.utils.serde.SchemaCache;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.storage.Converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Ales Justin
 */
public class ExtJsonConverter extends AbstractKafkaStrategyAwareSerDe<String, ExtJsonConverter> implements Converter {
    private final JsonConverter jsonConverter;
    private final ObjectMapper mapper;
    private FormatStrategy formatStrategy;

    private SchemaCache<JsonNode> cache;

    public ExtJsonConverter() {
        this(null);
    }

    public ExtJsonConverter(RegistryRestClient client) {
        super(client);
        this.jsonConverter = new JsonConverter();
        this.mapper = new ObjectMapper();
        this.formatStrategy = new PrettyFormatStrategy();
    }

    public ExtJsonConverter setFormatStrategy(FormatStrategy formatStrategy) {
        this.formatStrategy = Objects.requireNonNull(formatStrategy);
        return self();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        Map<String, Object> wrapper = new HashMap<>(configs);
        wrapper.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, false);
        jsonConverter.configure(wrapper, isKey);
    }

    private synchronized SchemaCache<JsonNode> getCache() {
        if (cache == null) {
            cache = new SchemaCache<JsonNode>(getClient()) {
                @Override
                protected JsonNode toSchema(InputStream schemaData) {
                    try {
                        return mapper.readTree(schemaData);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            };
        }
        return cache;
    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object value) {
        String schemaString = jsonConverter.asJsonSchema(schema).toString();
        String artifactId = getArtifactIdStrategy().artifactId(topic, isKey(), schemaString);
        long globalId = getGlobalIdStrategy().findId(getClient(), artifactId, ArtifactType.KCONNECT, schemaString);

        byte[] payload = jsonConverter.fromConnectData(topic, schema, value);

        return formatStrategy.fromConnectData(globalId, payload);
    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {
        FormatStrategy.IdPayload ip = formatStrategy.toConnectData(value);

        long globalId = ip.getGlobalId();
        JsonNode schemaNode = getCache().getSchema(globalId);
        Schema schema = jsonConverter.asConnectSchema(schemaNode);

        byte[] payload = ip.getPayload();
        SchemaAndValue sav = jsonConverter.toConnectData(topic, payload);

        return new SchemaAndValue(schema, sav.value());
    }
}
