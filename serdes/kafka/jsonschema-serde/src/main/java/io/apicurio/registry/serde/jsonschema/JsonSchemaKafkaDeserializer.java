package io.apicurio.registry.serde.jsonschema;

import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class JsonSchemaKafkaDeserializer<T> extends AbstractKafkaDeserializer<JsonSchema, T> {

    private MessageTypeSerdeHeaders serdeHeaders;
    private JsonSchemaDeserializer<T> jsonSchemaDeserializer;

    public JsonSchemaKafkaDeserializer() {
        super();
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client, SchemaResolver<JsonSchema, T> schemaResolver) {
        super(client, schemaResolver);
    }

    public JsonSchemaKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public JsonSchemaKafkaDeserializer(SchemaResolver<JsonSchema, T> schemaResolver) {
        super(schemaResolver);
    }

    /**
     * @see AbstractKafkaDeserializer#configure(java.util.Map, boolean)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        JsonSchemaDeserializerConfig config = new JsonSchemaDeserializerConfig(configs, isKey);
        this.jsonSchemaDeserializer = new JsonSchemaDeserializer<>();
        if (getSchemaResolver() != null) {
            this.jsonSchemaDeserializer.setSchemaResolver(getSchemaResolver());
        }
        jsonSchemaDeserializer.configure(config, isKey);

        this.serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
        super.configure(configs, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<JsonSchema, T> schemaParser() {
        return jsonSchemaDeserializer.schemaParser();
    }

    /**
     * @see AbstractKafkaDeserializer#readData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.nio.ByteBuffer, int, int)
     */
    @Override
    protected T readData(ParsedSchema<JsonSchema> schema, ByteBuffer buffer, int start, int length) {
        return jsonSchemaDeserializer.readData(schema, buffer, start, length);
    }

    @Override
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (headers != null && jsonSchemaDeserializer.getSpecificReturnClass() == null) {
            String javaType = serdeHeaders.getMessageType(headers);
            jsonSchemaDeserializer
                    .setSpecificReturnClass(javaType == null ? null : Utils.loadClass(javaType));
        }

        return super.deserialize(topic, headers, data);
    }
}
