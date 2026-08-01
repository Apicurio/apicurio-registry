package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;

import org.apache.kafka.common.header.Headers;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.kafka.KafkaDeserializer;
import io.apicurio.registry.serde.kafka.headers.MessageTypeSerdeHeaders;

public class JsonSchemaKafkaDeserializer<T> extends KafkaDeserializer<JsonSchema, T> {

    private MessageTypeSerdeHeaders serdeHeaders;

    public JsonSchemaKafkaDeserializer() {
        super(JsonSchemaDeserializer::new);
    }

    @Override
    protected void initializeHeaders(Map<String, ?> configs, boolean isKey) {
        this.serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
    }

    /**
     * Sets a custom Jackson ObjectMapper to use for deserialization. This allows users to configure
     * Jackson features such as custom modules (e.g., JavaTimeModule), serializers, deserializers,
     * and other ObjectMapper settings.
     *
     * <p>This method must be called before the {@link #configure(Map, boolean)} method is invoked,
     * as the configure method will create a default ObjectMapper if one has not been set.</p>
     *
     * @param objectMapper the ObjectMapper to use for deserialization
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        ((JsonSchemaDeserializer<T>) delegatedDeserializer).setObjectMapper(objectMapper);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (serdeHeaders != null && headers != null
                && ((JsonSchemaDeserializer<T>) delegatedDeserializer).getSpecificReturnClass() == null) {
            String javaType = serdeHeaders.getMessageType(headers);
            ((JsonSchemaDeserializer<T>) delegatedDeserializer)
                    .setSpecificReturnClass(javaType == null ? null : Utils.loadClass(javaType));
        }

        return super.deserialize(topic, headers, data);
    }
}
