/*
 * Copyright 2019 Red Hat
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.types.ArtifactType;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public class ExtJsonConverter extends AbstractKafkaStrategyAwareSerDe<String, ExtJsonConverter> implements Converter {
    private final JsonConverter jsonConverter;
    private final ObjectMapper mapper;

    private SchemaCache<JsonNode> cache;

    public ExtJsonConverter() {
        this(null);
    }

    public ExtJsonConverter(RegistryService client) {
        super(client);
        this.jsonConverter = new JsonConverter();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs);
        Map<String, Object> wrapper = new HashMap<>(configs);
        wrapper.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, false);
        jsonConverter.configure(wrapper, isKey);
    }

    private synchronized SchemaCache<JsonNode> getCache() {
        if (cache == null) {
            cache = new SchemaCache<JsonNode>(getClient()) {
                @Override
                protected JsonNode toSchema(Response response) {
                    try (InputStream stream = response.readEntity(InputStream.class)) {
                        return mapper.readTree(stream);
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
        long globalId = getGlobalIdStrategy().findId(getClient(), artifactId, ArtifactType.JSON, schemaString);

        byte[] bytes = jsonConverter.fromConnectData(topic, schema, value);
        String payload = new String(bytes, StandardCharsets.UTF_8); // TODO -- use IoUtil

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("id", globalId);
        root.putRawValue("payload", new RawValue(payload));
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {
        try {
            JsonNode root = mapper.readTree(value);

            long globalId = root.get("id").asLong();
            JsonNode schemaNode = getCache().getSchema(globalId);
            Schema schema = jsonConverter.asConnectSchema(schemaNode);

            String payload = root.get("payload").toString();
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8); // TODO -- use IoUtil
            SchemaAndValue sav = jsonConverter.toConnectData(topic, bytes);

            return new SchemaAndValue(schema, sav.value());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
